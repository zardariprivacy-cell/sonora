package com.nawazishali.sonora;

import android.app.Activity;

/** Base class for the five bottom-navigation tabs. */
public abstract class Tab {
    protected final Activity act;
    protected android.view.View root;

    protected Tab(Activity act) { this.act = act; }

    public final android.view.View view() {
        if (root == null) root = create();
        return root;
    }

    protected abstract android.view.View create();
    public void refresh() { }
    public void onActivityResult(int requestCode, int resultCode, android.content.Intent data) { }
}
