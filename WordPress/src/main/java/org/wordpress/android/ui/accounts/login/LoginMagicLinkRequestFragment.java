package org.wordpress.android.ui.accounts.login;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.fluxc.Dispatcher;
import org.wordpress.android.fluxc.generated.AuthenticationActionBuilder;
import org.wordpress.android.fluxc.store.AccountStore;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.GravatarUtils;
import org.wordpress.android.util.NetworkUtils;
import org.wordpress.android.util.ToastUtils;
import org.wordpress.android.widgets.WPNetworkImageView;

import javax.inject.Inject;

public class LoginMagicLinkRequestFragment extends Fragment {
    public static final String TAG = "login_magic_link_request_fragment_tag";

    private static final String KEY_IN_PROGRESS = "KEY_IN_PROGRESS";
    private static final String ARG_EMAIL_ADDRESS = "ARG_EMAIL_ADDRESS";

    private LoginListener mLoginListener;

    private String mEmail;

    private Button mRequestMagicLinkButton;
    private ProgressDialog mProgressDialog;

    private boolean mInProgress;

    protected @Inject Dispatcher mDispatcher;

    public static LoginMagicLinkRequestFragment newInstance(String email) {
        LoginMagicLinkRequestFragment fragment = new LoginMagicLinkRequestFragment();
        Bundle args = new Bundle();
        args.putString(ARG_EMAIL_ADDRESS, email);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((WordPress) getActivity().getApplication()).component().inject(this);

        if (getArguments() != null) {
            mEmail = getArguments().getString(ARG_EMAIL_ADDRESS);
        }

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.login_magic_link_request_screen, container, false);
        mRequestMagicLinkButton = (Button) view.findViewById(R.id.login_request_magic_link);
        mRequestMagicLinkButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mLoginListener != null) {
                    if (NetworkUtils.checkConnection(getActivity())) {
                        showMagicLinkRequestProgressDialog();
                        mDispatcher.dispatch(AuthenticationActionBuilder.newSendAuthEmailAction(mEmail));
                    }
                }
            }
        });

        view.findViewById(R.id.login_enter_password).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mLoginListener != null) {
                    mLoginListener.usePasswordInstead(mEmail);
                }
            }
        });

        WPNetworkImageView avatarView = (WPNetworkImageView) view.findViewById(R.id.gravatar);
        avatarView.setImageUrl(GravatarUtils.gravatarFromEmail(mEmail, getContext().getResources()
                .getDimensionPixelSize(R.dimen.avatar_sz_login)), WPNetworkImageView.ImageType.AVATAR);

        return view;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Toolbar toolbar = (Toolbar) view.findViewById(R.id.toolbar);
        ((AppCompatActivity) getActivity()).setSupportActionBar(toolbar);

        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (savedInstanceState != null) {
            mInProgress = savedInstanceState.getBoolean(KEY_IN_PROGRESS);
            if (mInProgress) {
                showMagicLinkRequestProgressDialog();
            }
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof LoginListener) {
            mLoginListener = (LoginListener) context;
        } else {
            throw new RuntimeException(context.toString() + " must implement LoginListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mLoginListener = null;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putBoolean(KEY_IN_PROGRESS, mInProgress);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_login, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.help) {
            if (mLoginListener != null) {
                mLoginListener.help();
            }
            return true;
        }

        return false;
    }

    @Override
    public void onStart() {
        super.onStart();
        mDispatcher.register(this);
    }

    @Override
    public void onStop() {
        super.onStop();
        mDispatcher.unregister(this);
    }

    private void showMagicLinkRequestProgressDialog() {
        startProgress(getString(R.string.login_magic_link_email_requesting));
    }

    protected void startProgress(String message) {
        mRequestMagicLinkButton.setEnabled(false);
        mProgressDialog = ProgressDialog.show(getActivity(), "", message, true, true,
                new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        if (mInProgress) {
                            endProgress();
                        }
                    }
                });
        mInProgress = true;
    }

    protected void endProgress() {
        mInProgress = false;

        if (mProgressDialog != null) {
            mProgressDialog.cancel();
            mProgressDialog = null;
        }

        // nullify the reference to denote there is no operation in progress
        mProgressDialog = null;

        mRequestMagicLinkButton.setEnabled(true);
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onAuthEmailSent(AccountStore.OnAuthEmailSent event) {
        if (!mInProgress) {
            // ignore the response if the magic link request is no longer pending
            return;
        }

        endProgress();

        if (event.isError()) {
            AppLog.e(AppLog.T.API, "OnAuthEmailSent has error: " + event.error.type + " - " + event.error.message);
            if (isAdded()) {
                ToastUtils.showToast(getActivity(), R.string.magic_link_unavailable_error_message, ToastUtils
                        .Duration.LONG);
            }
            return;
        }

        if (mLoginListener != null) {
            mLoginListener.showMagicLinkSentScreen(mEmail);
        }
    }
}