package com.boko.vimusic.ui.fragments;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.boko.vimusic.R;

public class SlideMenuFragment extends ListFragment {
	
	private int itemHeaderColor;
	private ListAdapter adapter;

	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		return inflater.inflate(R.layout.slidingmenu_list, null);
	}

	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		super.setListAdapter(adapter);
	}

	public class MenuItem {
		public String text;
		public String buttonText;
		public int iconRes;
		public int type;
		public static final int TYPE_HEADER = 0;
		public static final int TYPE_GROUP = 1;
		public static final int TYPE_ITEM = 2;
		public static final int TYPE_BUTTON_ITEM = 3;
		
		public MenuItem(String text, String buttonText, int iconRes, int type) {
			super();
			this.text = text;
			this.buttonText = buttonText;
			this.iconRes = iconRes;
			this.type = type;
		}
		public MenuItem(String text, int iconRes, int type) {
			super();
			this.text = text;
			this.iconRes = iconRes;
			this.type = type;
		}
		public MenuItem(String text, int type) {
			super();
			this.text = text;
			this.type = type;
		}
		public MenuItem(int iconRes, int type) {
			super();
			this.iconRes = iconRes;
			this.type = type;
		}
	}

	public class MenuAdapter extends ArrayAdapter<MenuItem> {

		public MenuAdapter(Context context) {
			super(context, 0);
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) {
				convertView = LayoutInflater.from(getContext()).inflate(
						R.layout.slidingmenu_row, null);
			}
			MenuItem item = getItem(position);
			
			ImageView header = (ImageView) convertView
					.findViewById(R.id.slidingmenu_row_header);
			ImageView icon = (ImageView) convertView
					.findViewById(R.id.slidingmenu_row_icon);
			TextView title = (TextView) convertView
					.findViewById(R.id.slidingmenu_row_title);
			Button button = (Button) convertView
					.findViewById(R.id.slidingmenu_row_button);
			
			switch (item.type) {
			case MenuItem.TYPE_HEADER:
				header.setVisibility(View.GONE);
				title.setVisibility(View.GONE);
				button.setVisibility(View.GONE);
				icon.setImageResource(getItem(position).iconRes);
				break;
			case MenuItem.TYPE_GROUP:
				icon.setVisibility(View.GONE);
				button.setVisibility(View.GONE);
				header.setBackgroundColor(itemHeaderColor);
				title.setText(getItem(position).text);
				break;
			case MenuItem.TYPE_ITEM:
				header.setVisibility(View.GONE);
				button.setVisibility(View.GONE);
				icon.setImageResource(getItem(position).iconRes);
				title.setText(getItem(position).text);
				break;
			case MenuItem.TYPE_BUTTON_ITEM:
				header.setVisibility(View.GONE);
				icon.setImageResource(getItem(position).iconRes);
				title.setText(getItem(position).text);
				button.setText(getItem(position).buttonText);
				break;
			}

			return convertView;
		}

	}

	public void setItemHeaderColor(int itemHeaderColor) {
		this.itemHeaderColor = itemHeaderColor;
	}
	
	public void setListAdapter(ListAdapter adapter) {
		this.adapter = adapter;
	}
}