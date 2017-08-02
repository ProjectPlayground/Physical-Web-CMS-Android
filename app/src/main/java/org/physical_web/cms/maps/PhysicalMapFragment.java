package org.physical_web.cms.maps;


import android.app.Fragment;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import org.physical_web.cms.R;

/**
 * This fragment allows users to upload a map of the physical space and mark the location of the
 * beacons within it.
 */
public class PhysicalMapFragment extends Fragment {
    private static final String FRAGMENT_TITLE = "Beacon Map";
    public PhysicalMapFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(FRAGMENT_TITLE);
    }
}