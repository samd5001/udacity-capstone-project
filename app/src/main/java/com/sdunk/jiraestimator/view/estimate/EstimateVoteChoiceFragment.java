package com.sdunk.jiraestimator.view.estimate;

import android.os.Bundle;
import android.transition.TransitionInflater;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.sdunk.jiraestimator.R;
import com.sdunk.jiraestimator.databinding.FragmentEstimateVoteChoiceBinding;
import com.sdunk.jiraestimator.nearby.EstimateNearbyService;

import org.jetbrains.annotations.NotNull;

import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor
public class EstimateVoteChoiceFragment extends AbstractEstimateVoteFragment {

    private static final String ARG_CHOICE = "arg_choice";

    public static EstimateVoteChoiceFragment newInstance(String vote) {
        EstimateVoteChoiceFragment fragment = new EstimateVoteChoiceFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CHOICE, vote);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        hideAppBar();

        FragmentEstimateVoteChoiceBinding binding = DataBindingUtil.inflate(inflater, R.layout.fragment_estimate_vote_choice, container, false);

        if (getArguments() != null && getArguments().containsKey(ARG_CHOICE)) {
            binding.setVoteOption(getArguments().getString(ARG_CHOICE));
            binding.voteChoice.setOnClickListener((v) -> estimateNearbyService.resetVote());
        }

        return binding.getRoot();
    }
}