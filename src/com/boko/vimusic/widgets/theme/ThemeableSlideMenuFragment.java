package com.boko.vimusic.widgets.theme;

import android.os.Bundle;

import com.boko.vimusic.ui.fragments.SlideMenuFragment;
import com.boko.vimusic.utils.ThemeUtils;

public class ThemeableSlideMenuFragment extends SlideMenuFragment {
    /**
     * Resource name used to theme the slide menu item header color
     */
    private static final String HEADER_COLOR = "tpi_unselected_text_color";
    
    @Override
	public void onActivityCreated(Bundle savedInstanceState) {
        // Initialze the theme resources
        final ThemeUtils resources = new ThemeUtils(getActivity().getApplicationContext());
        // Theme the layout
        setItemHeaderColor(resources.getColor(HEADER_COLOR));
        
        super.onActivityCreated(savedInstanceState);
	}
}
