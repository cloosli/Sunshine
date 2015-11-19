package com.loosli.christian.sunshine.app;

import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

/**
 * A placeholder fragment containing a simple view.
 */
public class MainActivityFragment extends Fragment {
    final String[] weekForecast = new String[]{
            "test1",
            "test2"
    };
    private ArrayAdapter<String> mForecastAdapter;

    public MainActivityFragment() { }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        ListView forecastListView = (ListView) rootView.findViewById(R.id.listview_forecast);
        mForecastAdapter = new ArrayAdapter<String>(getActivity(), R.layout.list_item_forecast, R.id.list_item_forecast_textview, weekForecast);
        forecastListView.setAdapter(mForecastAdapter);
        return rootView;
    }
}
