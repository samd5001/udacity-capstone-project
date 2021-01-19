package com.sdunk.jiraestimator.view.estimate;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.InterstitialAd;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;
import com.sdunk.jiraestimator.R;
import com.sdunk.jiraestimator.adapters.GenericRVAdapter;
import com.sdunk.jiraestimator.api.APIUtils;
import com.sdunk.jiraestimator.databinding.ActivityEstimateBinding;
import com.sdunk.jiraestimator.databinding.EstimateSessionItemBinding;
import com.sdunk.jiraestimator.databinding.VoteCardItemBinding;
import com.sdunk.jiraestimator.db.DBExecutor;
import com.sdunk.jiraestimator.db.user.UserDatabase;
import com.sdunk.jiraestimator.model.EstimateUser;
import com.sdunk.jiraestimator.model.User;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import lombok.Getter;
import timber.log.Timber;

import static com.sdunk.jiraestimator.api.APIUtils.updateIssuePoints;
import static com.sdunk.jiraestimator.view.issues.IssueDetailFragment.ARG_ISSUE;

public class EstimateActivity extends AppCompatActivity {

    private static final String STATE_FRAGMENT = "state_fragment";

    private static final String STATE_HOST_LIST = "state_host_list";

    private static final String STATE_USER_LIST = "state_user_list";

    private static final String STATE_USER_RESULTS = "state_user_results";

    private static final String STATE_USER_NAMES = "state_user_names";

    private static final String START_VOTE = "START_VOTE";

    private static final String START_DECIDER = "START_DECIDER";

    private static final String VOTE_SUCCESS = "VOTE_SUCCESS";

    private static final String VOTE_ERROR = "VOTE_ERROR";

    private static final Strategy NEARBY_STRATEGY = Strategy.P2P_STAR;

    private static final String[] REQUIRED_PERMISSIONS =
            new String[]{
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
            };

    private static final int REQUEST_CODE_REQUIRED_PERMISSIONS = 1;

    private boolean isAdvertising = false;

    private boolean isDiscovering = false;

    private String currentFragment;

    private String issueKey;

    private ArrayList<EstimateUser> hosts;

    private ArrayList<EstimateUser> users;

    private ArrayList<String> userNames;

    private HashMap<String, String> userEstimates;

    private GenericRVAdapter<EstimateUser, EstimateSessionItemBinding> sessionListAdapter;

    private GenericRVAdapter<String, EstimateSessionItemBinding> hostListAdapter;

    private User user;

    /**
     * EndpointDiscoveryCallback for updating the session list on the first fragment.
     */
    private final EndpointDiscoveryCallback endpointDiscoveryCallback =
            new EndpointDiscoveryCallback() {
                @Override
                public void onEndpointFound(@NotNull String endpointId, @NotNull DiscoveredEndpointInfo info) {
                    handleEndpointFound(endpointId, info);
                }

                @Override
                public void onEndpointLost(@NotNull String endpointId) {
                    handleEndpointLost(endpointId);
                }


            };

    /**
     * PayloadCallback for handling received payloads as a host.
     */
    private final PayloadCallback hostPayloadCallback =
            new PayloadCallback() {
                @Override
                public void onPayloadReceived(@NotNull String endpointId, @NotNull Payload payload) {
                    updateUserEstimates(endpointId, payload);
                    doEstimation();
                }

                @Override
                public void onPayloadTransferUpdate(@NotNull String endpointId, @NotNull PayloadTransferUpdate update) {
                }
            };

    private String hostEndpoint;

    private ConnectionsClient connectionsClient;

    /**
     * ConnectionLifecycleCallback for handling connecting to a user device as a host
     */
    private final ConnectionLifecycleCallback hostConnectionLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(@NotNull String endpointId, @NotNull ConnectionInfo connectionInfo) {
                    Timber.d("onConnectionInitiated: accepting connection");
                    connectionsClient.acceptConnection(endpointId, hostPayloadCallback);

                    Timber.d("onConnectionInitiated: accepting connection");
                    users.add(new EstimateUser(endpointId, connectionInfo.getEndpointName()));
                }

                @Override
                public void onConnectionResult(@NotNull String endpointId, ConnectionResolution result) {
                    if (result.getStatus().isSuccess()) {
                        Timber.i("onConnectionResult: connection successful");

                        EstimateUser newUser = getUserForEndpointID(endpointId);

                        if (newUser != null) {

                            userNames.add(newUser.getEmail());
                            userEstimates.put(endpointId, null);

                            sendExistingUserInfoPayloadsToNewUser(newUser);
                            sendNewUserInfoPayloadToExistingUsers(newUser);

                            hostListAdapter.notifyDataSetChanged();
                            Toast.makeText(getBaseContext(), getString(R.string.user_connected_message, newUser.getEmail()), Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        removeUserFromList(users, endpointId);
                        Timber.i("onConnectionResult: connection failed");
                    }
                }

                @Override
                public void onDisconnected(@NotNull String endpointId) {
                    Timber.i("onDisconnected: endpoint disconnected");
                    EstimateUser disconnectedUser = getUserForEndpointID(endpointId);

                    sendNewUserInfoPayloadToExistingUsers(disconnectedUser);
                    userNames.remove(disconnectedUser.getEmail());
                    userEstimates.remove(endpointId);
                    removeUserFromList(users, endpointId);
                    Toast.makeText(getBaseContext(), getString(R.string.user_connected_message, disconnectedUser.getEmail()), Toast.LENGTH_SHORT).show();
                }
            };
    
    private boolean votingFinished = false;

    @Getter
    private ActivityEstimateBinding binding;

    @Getter
    private boolean hosting = false;

    /**
     * PayloadCallback for handling received payloads as a user
     */
    private final PayloadCallback userPayloadCallback =
            new PayloadCallback() {
                @Override
                public void onPayloadReceived(@NotNull String endpointId, @NotNull Payload payload) {
                    handlePayloadFromHost(payload);
                }

                @Override
                public void onPayloadTransferUpdate(@NotNull String endpointId, @NotNull PayloadTransferUpdate update) {
                }
            };

    /**
     * ConnectionLifecycleCallback for handling connecting to a host devices
     */
    private final ConnectionLifecycleCallback userLifecycleCallback =
            new ConnectionLifecycleCallback() {
                @Override
                public void onConnectionInitiated(@NotNull String endpointId, @NotNull ConnectionInfo connectionInfo) {
                    Timber.i("onConnectionInitiated: accepting connection");
                    connectionsClient.acceptConnection(endpointId, userPayloadCallback);
                    userNames.add(getHostName(connectionInfo.getEndpointName()));
                    hostEndpoint = endpointId;
                }

                @Override
                public void onConnectionResult(@NotNull String endpointId, ConnectionResolution result) {
                    if (result.getStatus().isSuccess()) {
                        Timber.i("onConnectionResult: connection successful");
                        switchToSessionHostFragment();
                    } else {
                        Timber.i("onConnectionResult: connection failed");
                        clearUserData();
                    }
                }

                @Override
                public void onDisconnected(@NotNull String endpointId) {
                    if (!votingFinished) {
                        Timber.i("onDisconnected: endpoint disconnected");
                        resetActivity();
                        Toast.makeText(EstimateActivity.this, R.string.error_host_disconnect, Toast.LENGTH_LONG).show();
                    }
                }
            };

    /**
     *
     */
    private static boolean hasPermissions(Context context, String... permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Updates the stored estimate for a user from a payload.
     *
     * @param endpointId Endpoint id for user.
     * @param payload    Received payload message.
     */
    private void updateUserEstimates(@NotNull String endpointId, @NotNull Payload payload) {
        userEstimates.replace(endpointId, new String(payload.asBytes(), StandardCharsets.UTF_8));
    }

    /**
     * Parses a host payload and handles the payload based on the value
     */
    private void handlePayloadFromHost(@NotNull Payload payload) {
        String[] payloadArray = new String(payload.asBytes(), StandardCharsets.UTF_8).split("%");

        switch (payloadArray[0]) {
            case START_VOTE:
                startVoting(false);
                break;
            case START_DECIDER:
                switchToDeciderFragment(payloadArray[1], payloadArray[2]);
                break;
            case VOTE_SUCCESS:
                handleSuccessfulVote(payloadArray[1]);
                break;
            case VOTE_ERROR:
                handleFailedVote(payloadArray[1]);
                break;
            default:
                updateUsers(payloadArray[0]);
        }
    }

    /**
     * Adds or removes a user from the userName list
     */
    private void updateUsers(String userName) {
        if (userNames.contains(userName)) {
            userNames.remove(userName);
        } else {
            userNames.add(userName);
        }
        hostListAdapter.notifyDataSetChanged();
    }

    /**
     * Checks if all user votes have been collected and will either send the most common value to the API.
     * <b/>
     * If there is not a clear winner, the another round of voting will start.
     */
    private void doEstimation() {
        if (userEstimates.entrySet().stream().allMatch(result -> result.getValue() != null && !result.getValue().isEmpty())) {

            HashMap<String, Integer> choiceCountMap = new HashMap<>();
            userEstimates.values().forEach(value -> {
                Integer currentCount = choiceCountMap.get(value);
                if (currentCount == null) {
                    choiceCountMap.put(value, 0);
                } else {
                    choiceCountMap.put(value, currentCount + 1);
                }
            });

            if (choiceCountMap.values().size() == 1) {
                choiceCountMap
                        .keySet()
                        .stream()
                        .findFirst()
                        .ifPresent((choice) -> updateIssuePoints(user, issueKey, choice, this));
            } else {
                choiceCountMap
                        .entrySet()
                        .stream()
                        .max(Entry.comparingByValue())
                        .ifPresent((topChoice) -> {
                            List<String> topVoteChoices = choiceCountMap.entrySet().stream().filter(entry -> entry.getValue().equals(topChoice.getValue())).map(Entry::getKey).collect(Collectors.toList());
                            if (topVoteChoices.size() == 2) {
                                userEstimates.replaceAll((key, value) -> null);
                                topVoteChoices.sort(String::compareToIgnoreCase);
                                sendDeciderOptionsPayload(topVoteChoices.get(0), topVoteChoices.get(1));
                                switchToDeciderFragment(topVoteChoices.get(0), topVoteChoices.get(1));
                            }
                        });
            }

        }
    }

    private EstimateUser getUserForEndpointID(@NotNull String endpointId) {
        return users.stream().filter(user -> user.getEndpointId().equals(endpointId)).findFirst().orElse(null);
    }

    /**
     * Resets the activity to the first fragment and resets Nearby Connections
     */
    private void resetActivity() {
        stopConnections();
        startDiscovery();
        clearUserData();
        setupSessionListFragment();
    }

    /**
     * Sends details for every existing user to a new user.
     *
     * @param newUser User to send existing users to
     */
    private void sendExistingUserInfoPayloadsToNewUser(EstimateUser newUser) {
        users.stream().filter(user -> !user.equals(newUser)).forEach(u -> sendUserInfoPayloadToEndpoint(u, newUser.getEndpointId()));
    }

    /**
     * Sends a new user email to all connected endpoints.
     *
     * @param newUser User to be sent
     */
    private void sendNewUserInfoPayloadToExistingUsers(EstimateUser newUser) {
        sendPayloadToAllUsers(newUser.getEmail());
    }

    private void sendPayloadToAllUsers(String email) {
        Payload newUserPayload = Payload.fromBytes(email.getBytes());
        connectionsClient.sendPayload(
                users
                        .stream()
                        .map(EstimateUser::getEndpointId)
                        .collect(Collectors.toList()),
                newUserPayload
        );
    }

    /**
     *
     */
    private void sendUserInfoPayloadToEndpoint(EstimateUser user, String userEndpoint) {
        Payload payload = Payload.fromBytes(user.getEmail().getBytes());
        connectionsClient.sendPayload(userEndpoint, payload);
    }

    /**
     *
     */
    private void sendVotePayload(String vote) {
        Payload payload = Payload.fromBytes(vote.getBytes());
        connectionsClient.sendPayload(hostEndpoint, payload);
    }

    /**
     *
     */
    private void sendDeciderOptionsPayload(String optionA, String optionB) {
        sendPayloadToAllUsers(START_DECIDER + "%" + optionA + "%" + optionB);
    }

    /**
     *
     */
    private void startUserVoteSessions() {
        sendPayloadToAllUsers(START_VOTE);
    }

    /**
     *
     */
    private void sendVoteResultPayloads(String voteStatus, String voteValue) {
        sendPayloadToAllUsers(voteStatus + "%" + voteValue);
    }


    /**
     *
     */
    private String getHostName(String endpointName) {
        String[] userData = endpointName.split("%");
        if (userData.length == 2 && userData[0].equals(issueKey) && !userData[1].equalsIgnoreCase(user.getEmail())) {
            return userData[1];
        }

        return null;
    }

    /**
     *
     */
    private void removeUserFromList(List<EstimateUser> users, @NotNull String endpointId) {
        users.removeIf((user) -> user.getEndpointId().equals(endpointId));
    }

    /**
     *
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString(ARG_ISSUE, issueKey);
        outState.putString(STATE_FRAGMENT, currentFragment);
        outState.putParcelableArrayList(STATE_HOST_LIST, hosts);
        outState.putParcelableArrayList(STATE_USER_LIST, users);
        outState.putSerializable(STATE_USER_RESULTS, userEstimates);
        outState.putStringArrayList(STATE_USER_NAMES, userNames);
        super.onSaveInstanceState(outState);
    }

    /**
     *
     */
    @Override
    @SuppressWarnings("unchecked")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_estimate);
        connectionsClient = Nearby.getConnectionsClient(this);

        if (savedInstanceState == null) {

            loadAndShowInterstitialAd();

            if (getIntent().hasExtra(ARG_ISSUE)) {
                issueKey = getIntent().getStringExtra(ARG_ISSUE);
            }

            hosts = new ArrayList<>();
            users = new ArrayList<>();
            userEstimates = new HashMap<>();
            userNames = new ArrayList<>();

            setupSessionListFragment();
        } else {
            issueKey = savedInstanceState.getString(ARG_ISSUE);
            hosts = savedInstanceState.getParcelableArrayList(STATE_HOST_LIST);
            users = savedInstanceState.getParcelableArrayList(STATE_USER_LIST);
            userEstimates = (HashMap<String, String>) savedInstanceState.getSerializable(STATE_USER_RESULTS);
            userNames = savedInstanceState.getStringArrayList(STATE_USER_NAMES);
        }

        binding = DataBindingUtil.setContentView(this, R.layout.activity_estimate);

        setSupportActionBar(binding.estimateToolbar);

        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        getUserFromDB();
    }

    /**
     *
     */
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            FragmentManager fragmentManager = getSupportFragmentManager();
            if (fragmentManager.getBackStackEntryCount() > 0) {
                handleBackPress(fragmentManager);
            } else {
                finish();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {

        FragmentManager fragmentManager = getSupportFragmentManager();
        if (fragmentManager.getBackStackEntryCount() > 0) {
            handleBackPress(fragmentManager);
        } else {
            super.onBackPressed();
        }
    }

    private void handleBackPress(FragmentManager fragmentManager) {
        fragmentManager.popBackStack();
        if (fragmentManager.getBackStackEntryCount() == 1) {
            resetNearbyConnections();
        }
    }

    /**
     *
     */
    @Override
    protected void onStart() {
        super.onStart();

        if (!hasPermissions(this, REQUIRED_PERMISSIONS)) {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_REQUIRED_PERMISSIONS);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopConnections();
    }

    /**
     *
     */
    private void startDiscovery() {

        connectionsClient
                .startDiscovery(getPackageName(), endpointDiscoveryCallback, new DiscoveryOptions.Builder().setStrategy(NEARBY_STRATEGY).build())
                .addOnSuccessListener((unused) -> {
                    Timber.d("Nearby Discovery started");
                    isDiscovering = true;
                })
                .addOnFailureListener((e) -> Timber.e(e, "Nearby Discovery failed to start"));
    }

    /**
     *
     */
    private void startAdvertising() {
        clearUserData();

        userEstimates.put(user.getEmail() + 20, null);
        userNames.add(user.getEmail() + 20);

        connectionsClient
                .startAdvertising(getHostEndpointName(), getPackageName(), hostConnectionLifecycleCallback, new AdvertisingOptions.Builder().setStrategy(NEARBY_STRATEGY).build())
                .addOnSuccessListener((unused) -> {
                    Timber.d("Nearby Advertising started");
                    isAdvertising = true;
                })
                .addOnFailureListener((e) -> Timber.e(e, "Nearby Advertising failed to start"));

    }

    /**
     *
     */
    private void stopDiscovery() {
        if (isDiscovering) {
            connectionsClient.stopDiscovery();
            isDiscovering = false;
        }
        hosts.clear();
    }

    /**
     *
     */
    private void stopAdvertising() {
        if (isAdvertising) {
            connectionsClient.stopAdvertising();
            isAdvertising = false;
        }
    }

    /**
     *
     */
    @NotNull
    private String getHostEndpointName() {
        return issueKey + "%" + user.getEmail() + 20;
    }

    /**
     *
     */
    public void startHostingSession() {
        startAdvertising();
        hosting = true;
        switchToSessionHostFragment();
    }

    /**
     *
     */
    private void setupSessionListFragment() {

        sessionListAdapter = new GenericRVAdapter<EstimateUser, EstimateSessionItemBinding>(hosts) {
            @Override
            public int getLayoutResId() {
                return R.layout.estimate_session_item;
            }

            @Override
            public void onBindData(EstimateUser hostName, int position, EstimateSessionItemBinding binding) {
                binding.setUserName(hostName.getEmail());
            }

            @Override
            public void onItemClick(EstimateUser hostName, int position) {
                connectionsClient.requestConnection(user.getEmail(), hostName.getEndpointId(), userLifecycleCallback);
            }
        };

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.estimate_fragment_container, EstimateSessionListFragment.newInstance(sessionListAdapter))
                .commit();

        currentFragment = EstimateSessionListFragment.FRAGMENT_LIST;
    }

    /**
     *
     */
    private void stopConnections() {
        stopDiscovery();
        stopAdvertising();
        connectionsClient.stopAllEndpoints();
        clearUserData();
    }

    /**
     *
     */
    private void clearUserData() {
        userNames.clear();
        users.clear();
        userEstimates.clear();
    }

    /**
     *
     */
    private void switchToSessionHostFragment() {

        stopDiscovery();

        hostListAdapter = new GenericRVAdapter<String, EstimateSessionItemBinding>(userNames) {
            @Override
            public int getLayoutResId() {
                return R.layout.estimate_session_item;
            }

            @Override
            public void onBindData(String user, int position, EstimateSessionItemBinding binding) {
                binding.setUserName(user);
            }

            @Override
            public void onItemClick(String hostName, int position) {
            }
        };

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.estimate_fragment_container, EstimateSessionHostFragment.newInstance(hosting, hostListAdapter))
                .addToBackStack("session_list")
                .commit();

        currentFragment = EstimateSessionHostFragment.FRAGMENT_HOST;
    }

    /**
     *
     */
    public void startVoting(boolean sendStartVotePayload) {

        if (sendStartVotePayload) {
            startUserVoteSessions();
        }

        ArrayList<String> voteOptions = new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.estimate_options)));

        GenericRVAdapter<String, VoteCardItemBinding> voteOptionAdapter = new GenericRVAdapter<String, VoteCardItemBinding>(voteOptions) {

            @Override
            public int getLayoutResId() {
                return R.layout.vote_card_item;
            }

            @Override
            public void onBindData(String vote, int position, VoteCardItemBinding binding) {
                binding.setVoteOption(vote);
            }

            @Override
            public void onItemClick(String vote, int position) {
                submitVote(vote);
            }
        };

        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.popBackStack();
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.estimate_fragment_container, EstimateVoteFragment.newInstance(voteOptionAdapter))
                .addToBackStack("session_list")
                .commit();

        currentFragment = EstimateSessionHostFragment.FRAGMENT_HOST;
    }

    /**
     *
     */
    public void submitVote(String vote) {
        if (hosting) {
            userEstimates.put(user.getEmail() + 20, vote);
            doEstimation();
        } else {
            sendVotePayload(vote);
        }

        switchToChoiceFragment(vote);
    }

    /**
     *
     */
    public void resetVote() {
        boolean sendStartVote = false;
        if (!hosting) {
            sendVotePayload("");
        } else {
            if (!userEstimates.containsKey(user.getEmail() + 20)) {
                sendStartVote = true;
            }
            userEstimates.put(user.getEmail() + 20, null);
        }
        startVoting(sendStartVote);
    }

    /**
     *
     */
    private void switchToChoiceFragment(String vote) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.estimate_fragment_container, EstimateVoteChoiceFragment.newInstance(vote))
                .commit();
    }

    /**
     *
     */
    private void switchToDeciderFragment(String optionA, String optionB) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.estimate_fragment_container, EstimateVoteDeciderFragment.newInstance(optionA, optionB))
                .commit();
    }

    /**
     *
     */
    public void resetNearbyConnections() {
        clearUserData();
        stopConnections();
        startDiscovery();
    }

    /**
     *
     */
    public void handleSuccessfulVote(String vote) {
        if (isHosting()) {
            sendVoteResultPayloads(VOTE_SUCCESS, vote);
        }
        new APIUtils(this).updateIssueCache();
        String toastMessage = vote.equals("?") ? getString(R.string.undecided_vote_response) : getString(R.string.successful_vote_response, vote) ;
        handleVoteFinish(toastMessage);
    }

    /**
     *
     */
    public void handleFailedVote(String vote) {
        if (isHosting()) {
            sendVoteResultPayloads(VOTE_ERROR, vote);
        }
        handleVoteFinish(getString(R.string.error_vote_response, vote));
    }

    private void handleVoteFinish(String toastMessage) {
        votingFinished = true;
        Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show();
        stopConnections();
        finish();
    }


    /**
     *
     */
    private void handleEndpointLost(@NotNull String endpointId) {
        Timber.i("onEndpointLost: endpoint lost, removing from endpoint list");
        removeUserFromList(hosts, endpointId);
        sessionListAdapter.notifyDataSetChanged();
    }

    /**
     *
     */
    private void handleEndpointFound(@NotNull String endpointId, @NotNull DiscoveredEndpointInfo info) {
        Timber.i("onEndpointFound: endpoint found, adding to host list");

        String hostName = getHostName(info.getEndpointName());
        if (hostName != null) {
            hosts.add(new EstimateUser(endpointId, hostName));
            sessionListAdapter.notifyDataSetChanged();
        }
    }

    /**
     *
     */
    private void getUserFromDB() {
        DBExecutor.getInstance().diskIO().execute(() -> {
            user = UserDatabase.getInstance(EstimateActivity.this).userDao().getLoggedInUser();
            startDiscovery();
        });
    }

    /**
     *
     */
    private void loadAndShowInterstitialAd() {
        MobileAds.initialize(this);
        RequestConfiguration requestConfiguration = new RequestConfiguration.Builder().setTestDeviceIds(Collections.singletonList("34C1F882D6CC2F3D16893974B7CEEC6C")).build();
        MobileAds.setRequestConfiguration(requestConfiguration);

        InterstitialAd interstitialAd = new InterstitialAd(this);
        interstitialAd.setAdUnitId(getString(R.string.test_interstitial_ad_id));
        interstitialAd.loadAd(new AdRequest.Builder().build());
        interstitialAd.setAdListener(new AdListener() {
            @Override
            public void onAdLoaded() {
                interstitialAd.show();
            }
        });
    }
}