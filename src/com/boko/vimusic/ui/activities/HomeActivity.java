/*
 * Copyright (C) 2012 Andrew Neal Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */

package com.boko.vimusic.ui.activities;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.view.ViewPager;

import com.boko.vimusic.R;
import com.boko.vimusic.ui.fragments.SlideMenuFragment;
import com.boko.vimusic.ui.fragments.phone.MusicBrowserPhoneFragment;
import com.boko.vimusic.widgets.theme.ThemeableSlideMenuFragment;
import com.jeremyfeinstein.slidingmenu.SlidingMenu;

/**
 * This class is used to display the {@link ViewPager} used to swipe between the
 * main {@link Fragment}s used to browse the user's music.
 * 
 * @author Andrew Neal (andrewdneal@gmail.com)
 */
public class HomeActivity extends BaseActivity {

	private SlidingMenu menu;
	private ThemeableSlideMenuFragment fragment;

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// Load the music browser fragment
		if (savedInstanceState == null) {
			// configure the SlidingMenu
			menu = new SlidingMenu(this);
			menu.setTouchModeAbove(SlidingMenu.TOUCHMODE_MARGIN);
			menu.setShadowWidthRes(R.dimen.shadow_width);
			menu.setShadowDrawable(R.drawable.shadow);
			menu.setBehindOffsetRes(R.dimen.slidingmenu_offset);
			menu.setFadeDegree(0.35f);
			menu.attachToActivity(this, SlidingMenu.SLIDING_WINDOW);
			menu.setMenu(R.layout.slidingmenu_frame);

			fragment = new ThemeableSlideMenuFragment();
			getSupportFragmentManager().beginTransaction()
					.replace(R.id.slidingmenu_frame, fragment).commit();
			SlideMenuFragment.MenuAdapter adapter = fragment.new MenuAdapter(
					this);
			initMenu(adapter);
			fragment.setListAdapter(adapter);

			getSupportFragmentManager()
					.beginTransaction()
					.replace(R.id.activity_base_content,
							new MusicBrowserPhoneFragment()).commit();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int setContentView() {
		return R.layout.activity_base;
	}

	private void initMenu(SlideMenuFragment.MenuAdapter adapter) {
		adapter.add(fragment.new MenuItem("Zing",
				SlideMenuFragment.MenuItem.TYPE_GROUP));
		adapter.add(fragment.new MenuItem("Account",
				android.R.drawable.ic_menu_search,
				SlideMenuFragment.MenuItem.TYPE_ITEM));
		adapter.add(fragment.new MenuItem("Favorite",
				android.R.drawable.ic_menu_search,
				SlideMenuFragment.MenuItem.TYPE_ITEM));
		adapter.add(fragment.new MenuItem("Playlist",
				android.R.drawable.ic_menu_search,
				SlideMenuFragment.MenuItem.TYPE_ITEM));
		adapter.add(fragment.new MenuItem("Downloads",
				android.R.drawable.ic_menu_search,
				SlideMenuFragment.MenuItem.TYPE_ITEM));

		adapter.add(fragment.new MenuItem("Zing Mp3",
				SlideMenuFragment.MenuItem.TYPE_GROUP));
		adapter.add(fragment.new MenuItem("Album",
				android.R.drawable.ic_menu_search,
				SlideMenuFragment.MenuItem.TYPE_ITEM));
		adapter.add(fragment.new MenuItem("Video clip",
				android.R.drawable.ic_menu_search,
				SlideMenuFragment.MenuItem.TYPE_ITEM));
		adapter.add(fragment.new MenuItem("BXH",
				android.R.drawable.ic_menu_search,
				SlideMenuFragment.MenuItem.TYPE_ITEM));
		adapter.add(fragment.new MenuItem("Topic",
				android.R.drawable.ic_menu_search,
				SlideMenuFragment.MenuItem.TYPE_ITEM));
	}
}
