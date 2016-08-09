package org.wordpress.android.ui.main;

import android.app.Fragment;
import android.support.annotation.StringRes;

import org.wordpress.android.R;

/**
 * MySites activity
 */
public class WPMainActivityMe extends WPMainActivityBottomBar {

    @Override
    protected int getBottomBarPosition() {
        return 2;
    }

    @Override
    protected @StringRes int getScreenTitle() {
        return R.string.tabbar_accessibility_label_me;
    }

    @Override
    protected Fragment newFragmentInstance() {
        return MeFragment.newInstance();
    }
}