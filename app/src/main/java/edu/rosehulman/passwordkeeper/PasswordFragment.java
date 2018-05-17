package edu.rosehulman.passwordkeeper;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.design.widget.Snackbar.Callback;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class PasswordFragment extends Fragment implements Toolbar.OnMenuItemClickListener, View.OnClickListener {

    private DatabaseReference mPasswordRef;
    private OnLogoutListener mListener;
    private PasswordAdapter mAdapter;

    public PasswordFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String firebasePath = getArguments().getString(Constants.FIREBASE_PATH);
        if (firebasePath == null || firebasePath.isEmpty()) {
            mPasswordRef = FirebaseDatabase.getInstance().getReference();
        } else {
            mPasswordRef = FirebaseDatabase.getInstance().getReference().child(firebasePath);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_password, container, false);
        // Setup Toolbar
        Toolbar mToolbar = (Toolbar) rootView.findViewById(R.id.toolbar);
        mToolbar.setTitle(R.string.app_name);
        getActivity().getMenuInflater().inflate(R.menu.main, mToolbar.getMenu());
        mToolbar.setOnMenuItemClickListener(this);
        mToolbar.setTitleTextColor(getResources().getColor(R.color.white));
        final View fab = rootView.findViewById(R.id.fab_add);
        fab.setOnClickListener(this);
        //Recycler View
        RecyclerView passwordList = (RecyclerView) rootView.findViewById(R.id.password_list);
        LinearLayoutManager manager = new LinearLayoutManager(getActivity());
        manager.setOrientation(LinearLayoutManager.VERTICAL);
        passwordList.setLayoutManager(manager);
        mAdapter = new PasswordAdapter(getActivity(), mPasswordRef);
        passwordList.setAdapter(mAdapter);
        ItemTouchHelper.SimpleCallback simpleItemTouchCallback = new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, ViewHolder viewHolder, ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir) {
                //Remove swiped item from list and notify the RecyclerView
                final int position = viewHolder.getAdapterPosition();
                final Password password = mAdapter.hide(position);
                final Snackbar snackbar = Snackbar
                        .make(fab, "Password removed!", Snackbar.LENGTH_LONG)
                        .setAction("UNDO", new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                mAdapter.insert(password, position);
                                Snackbar snackbar1 = Snackbar.make(fab, "Password restored!", Snackbar.LENGTH_SHORT);
                                snackbar1.show();
                            }
                        })
                        .setCallback(new Callback() {
                            @Override
                            public void onDismissed(Snackbar snackbar, int event) {
                                if (event != Callback.DISMISS_EVENT_ACTION && event != Callback.DISMISS_EVENT_CONSECUTIVE) {
                                    mAdapter.firebaseRemove(password);
                                }
                            }
                        });

                snackbar.show();
            }
        };
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(simpleItemTouchCallback);
        itemTouchHelper.attachToRecyclerView(passwordList);

        return rootView;
    }

    @Override
    public boolean onMenuItemClick(MenuItem menuItem) {
        int id = menuItem.getItemId();
        switch (id) {
            case R.id.action_logout:
                Log.d("PK", "LOGOUT Menu Item Clicked!");
                mListener.onLogout();
                return true;
        }
        return false;
    }

    @Override
    public void onClick(View view) {
        LayoutInflater inflater = LayoutInflater.from(getActivity());
        View contentView = inflater.inflate(R.layout.dialog_insert, null);
        final EditText serviceView = (EditText) contentView.findViewById(R.id.service);
        final EditText usernameView = (EditText) contentView.findViewById(R.id.username);
        final EditText passwordView = (EditText) contentView.findViewById(R.id.password);
        passwordView.setImeActionLabel("Create", EditorInfo.IME_NULL);
        final Dialog dialog = new AlertDialog.Builder(getActivity(),R.style.AlertDialogTheme)
                .setTitle(R.string.add_password_title)
                .setView(contentView)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.add, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Password pw = new Password(
                                null,
                                passwordView.getText().toString(),
                                serviceView.getText().toString());
                        String username = usernameView.getText().toString();
                        pw.setUsername(username.isEmpty() ? null : username);
                        mAdapter.firebasePush(pw);
                    }
                })
                .create();

        passwordView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == EditorInfo.IME_NULL) {
                    Password pw = new Password(
                            null,
                            passwordView.getText().toString(),
                            serviceView.getText().toString());
                    String username = usernameView.getText().toString();
                    pw.setUsername(username.isEmpty() ? null : username);
                    mAdapter.firebasePush(pw);
                    dialog.dismiss();
                    return true;
                }
                return false;
            }
        });
        dialog.show();
    }

    public interface OnLogoutListener {
        void onLogout();
    }

    @Override
    public void onPause() {
        super.onPause();
        mPasswordRef.removeEventListener(mAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        mAdapter.clear();
        mPasswordRef.addChildEventListener(mAdapter);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mListener = (OnLogoutListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString()
                    + " must implement OnLogoutListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }
}
