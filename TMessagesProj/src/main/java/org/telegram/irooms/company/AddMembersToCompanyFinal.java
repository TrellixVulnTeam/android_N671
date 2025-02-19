/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.irooms.company;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.text.InputFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.irooms.Constants;
import org.telegram.irooms.IRoomsManager;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.rooms.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.GroupCreateUserCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.ContextProgressView;
import org.telegram.ui.Components.EditTextEmoji;
import org.telegram.ui.Components.GroupCreateDividerItemDecoration;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.VerticalPositionAutoAnimator;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.LocationActivity;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class AddMembersToCompanyFinal extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private String action = "add";
    private GroupCreateAdapter adapter;
    private RecyclerListView listView;
    private EditTextEmoji editText;
    private ContextProgressView progressView;
    private AnimatorSet doneItemAnimation;
    private ImageView floatingButtonIcon;
    private FrameLayout floatingButtonContainer;

    private Drawable shadowDrawable;

    private ArrayList<Integer> selectedContacts;
    private boolean donePressed;
    private String nameToSet;
    private int chatType;

    private String currentGroupCreateAddress;
    private Location currentGroupCreateLocation;

    private int reqId;

    private final static int done_button = 1;
    private boolean createCompany;

    public interface GroupCreateFinalActivityDelegate {
        void didStartChatCreation();

        void didFinishChatCreation(AddMembersToCompanyFinal fragment, int chatId);

        void didFailChatCreation();
    }

    private GroupCreateFinalActivityDelegate delegate;

    public AddMembersToCompanyFinal(Bundle args) {
        super(args);
        createCompany = args.getBoolean("create_company");
        action = args.getString("action");
        chatType = args.getInt("chatType", ChatObject.CHAT_TYPE_CHAT);
        currentGroupCreateAddress = args.getString("address");
        currentGroupCreateLocation = args.getParcelable("location");
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatDidCreated);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.chatDidFailCreate);

        selectedContacts = getArguments().getIntegerArrayList("result");
        final ArrayList<Integer> usersToLoad = new ArrayList<>();
        for (int a = 0; a < selectedContacts.size(); a++) {
            Integer uid = selectedContacts.get(a);
            if (MessagesController.getInstance(currentAccount).getUser(uid) == null) {
                usersToLoad.add(uid);
            }
        }
        if (!usersToLoad.isEmpty()) {
            final CountDownLatch countDownLatch = new CountDownLatch(1);
            final ArrayList<TLRPC.User> users = new ArrayList<>();
            MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
                users.addAll(MessagesStorage.getInstance(currentAccount).getUsers(usersToLoad));
                countDownLatch.countDown();
            });
            try {
                countDownLatch.await();
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (usersToLoad.size() != users.size()) {
                return false;
            }
            if (!users.isEmpty()) {
                for (TLRPC.User user : users) {
                    MessagesController.getInstance(currentAccount).putUser(user, true);
                }
            } else {
                return false;
            }
        }
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.updateInterfaces);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatDidCreated);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.chatDidFailCreate);
        if (reqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
        }
        if (editText != null) {
            editText.onDestroy();
        }
        AndroidUtilities.removeAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (editText != null) {
            editText.onResume();
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (editText != null) {
            editText.onPause();
        }
    }

    @Override
    public void dismissCurrentDialog() {

        super.dismissCurrentDialog();
    }

    @Override
    public boolean dismissDialogOnPause(Dialog dialog) {
        return super.dismissDialogOnPause(dialog);
    }

    @Override
    public void onRequestPermissionsResultFragment(int requestCode, String[] permissions, int[] grantResults) {
    }

    @Override
    public boolean onBackPressed() {
        if (editText != null && editText.isPopupShowing()) {
            editText.hidePopup(true);
            return false;
        }
        return true;
    }

    @Override
    protected boolean hideKeyboardOnShow() {
        return false;
    }

    @Override
    public View createView(Context context) {
        if (editText != null) {
            editText.onDestroy();
        }

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        if (createCompany) {
            actionBar.setTitle("Create company");
        } else {
            actionBar.setTitle(PreferenceManager.getDefaultSharedPreferences(getParentActivity()).getString(Constants.SELECTED_COMPANY_NAME, ""));
        }


        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        SizeNotifierFrameLayout sizeNotifierFrameLayout = new SizeNotifierFrameLayout(context) {

            private boolean ignoreLayout;

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int widthSize = MeasureSpec.getSize(widthMeasureSpec);
                int heightSize = MeasureSpec.getSize(heightMeasureSpec);

                setMeasuredDimension(widthSize, heightSize);
                heightSize -= getPaddingTop();

                measureChildWithMargins(actionBar, widthMeasureSpec, 0, heightMeasureSpec, 0);

                int keyboardSize = measureKeyboardHeight();
                if (keyboardSize > AndroidUtilities.dp(20)) {
                    ignoreLayout = true;
                    editText.hideEmojiView();
                    ignoreLayout = false;
                }

                int childCount = getChildCount();
                for (int i = 0; i < childCount; i++) {
                    View child = getChildAt(i);
                    if (child == null || child.getVisibility() == GONE || child == actionBar) {
                        continue;
                    }
                    if (editText != null && editText.isPopupView(child)) {
                        if (AndroidUtilities.isInMultiwindow || AndroidUtilities.isTablet()) {
                            if (AndroidUtilities.isTablet()) {
                                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(Math.min(AndroidUtilities.dp(AndroidUtilities.isTablet() ? 200 : 320), heightSize - AndroidUtilities.statusBarHeight + getPaddingTop()), MeasureSpec.EXACTLY));
                            } else {
                                child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(heightSize - AndroidUtilities.statusBarHeight + getPaddingTop(), MeasureSpec.EXACTLY));
                            }
                        } else {
                            child.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(child.getLayoutParams().height, MeasureSpec.EXACTLY));
                        }
                    } else {
                        measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0);
                    }
                }
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                final int count = getChildCount();

                int keyboardSize = measureKeyboardHeight();
                int paddingBottom = keyboardSize <= AndroidUtilities.dp(20) && !AndroidUtilities.isInMultiwindow && !AndroidUtilities.isTablet() ? editText.getEmojiPadding() : 0;
                setBottomClip(paddingBottom);

                for (int i = 0; i < count; i++) {
                    final View child = getChildAt(i);
                    if (child.getVisibility() == GONE) {
                        continue;
                    }
                    final LayoutParams lp = (LayoutParams) child.getLayoutParams();

                    final int width = child.getMeasuredWidth();
                    final int height = child.getMeasuredHeight();

                    int childLeft;
                    int childTop;

                    int gravity = lp.gravity;
                    if (gravity == -1) {
                        gravity = Gravity.TOP | Gravity.LEFT;
                    }

                    final int absoluteGravity = gravity & Gravity.HORIZONTAL_GRAVITY_MASK;
                    final int verticalGravity = gravity & Gravity.VERTICAL_GRAVITY_MASK;

                    switch (absoluteGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
                        case Gravity.CENTER_HORIZONTAL:
                            childLeft = (r - l - width) / 2 + lp.leftMargin - lp.rightMargin;
                            break;
                        case Gravity.RIGHT:
                            childLeft = r - width - lp.rightMargin;
                            break;
                        case Gravity.LEFT:
                        default:
                            childLeft = lp.leftMargin;
                    }

                    switch (verticalGravity) {
                        case Gravity.TOP:
                            childTop = lp.topMargin + getPaddingTop();
                            break;
                        case Gravity.CENTER_VERTICAL:
                            childTop = ((b - paddingBottom) - t - height) / 2 + lp.topMargin - lp.bottomMargin;
                            break;
                        case Gravity.BOTTOM:
                            childTop = ((b - paddingBottom) - t) - height - lp.bottomMargin;
                            break;
                        default:
                            childTop = lp.topMargin;
                    }

                    if (editText != null && editText.isPopupView(child)) {
                        if (AndroidUtilities.isTablet()) {
                            childTop = getMeasuredHeight() - child.getMeasuredHeight();
                        } else {
                            childTop = getMeasuredHeight() + keyboardSize - child.getMeasuredHeight();
                        }
                    }
                    child.layout(childLeft, childTop, childLeft + width, childTop + height);
                }

                notifyHeightChanged();
            }

            @Override
            public void requestLayout() {
                if (ignoreLayout) {
                    return;
                }
                super.requestLayout();
            }
        };
        fragmentView = sizeNotifierFrameLayout;
        fragmentView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fragmentView.setOnTouchListener((v, event) -> true);

        shadowDrawable = context.getResources().getDrawable(R.drawable.greydivider_top).mutate();

        LinearLayout linearLayout = new LinearLayout(context) {
            @Override
            protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
                boolean result = super.drawChild(canvas, child, drawingTime);
                if (child == listView && shadowDrawable != null) {
                    shadowDrawable.draw(canvas);
                }
                return result;
            }
        };
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        sizeNotifierFrameLayout.addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0x55000000);


        editText = new EditTextEmoji(context, sizeNotifierFrameLayout, this, EditTextEmoji.STYLE_FRAGMENT);
        editText.setHint("Enter company name");
        if (nameToSet != null) {
            editText.setText(nameToSet);
            nameToSet = null;
        }
        InputFilter[] inputFilters = new InputFilter[1];
        inputFilters[0] = new InputFilter.LengthFilter(100);
        editText.setFilters(inputFilters);
        if (createCompany) {
            linearLayout.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 34, 24, 24, 24, 0));
        }

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);

        listView = new RecyclerListView(context);
        listView.setAdapter(adapter = new GroupCreateAdapter(context));
        listView.setLayoutManager(linearLayoutManager);
        listView.setVerticalScrollBarEnabled(false);
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? View.SCROLLBAR_POSITION_LEFT : View.SCROLLBAR_POSITION_RIGHT);
        GroupCreateDividerItemDecoration decoration = new GroupCreateDividerItemDecoration();
        decoration.setSkipRows(currentGroupCreateAddress != null ? 5 : 2);
        listView.addItemDecoration(decoration);
        linearLayout.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(editText);
                }
            }
        });
        listView.setOnItemClickListener((view, position) -> {
            if (view instanceof TextSettingsCell) {
                if (!AndroidUtilities.isGoogleMapsInstalled(AddMembersToCompanyFinal.this)) {
                    return;
                }
                LocationActivity fragment = new LocationActivity(LocationActivity.LOCATION_TYPE_GROUP);
                fragment.setDialogId(0);
                fragment.setDelegate((location, live, notify, scheduleDate) -> {
                    currentGroupCreateLocation.setLatitude(location.geo.lat);
                    currentGroupCreateLocation.setLongitude(location.geo._long);
                    currentGroupCreateAddress = location.address;
                });
                presentFragment(fragment);
            }
        });

        floatingButtonContainer = new FrameLayout(context);
        Drawable drawable = Theme.createSimpleSelectorCircleDrawable(AndroidUtilities.dp(56), Theme.getColor(Theme.key_chats_actionBackground), Theme.getColor(Theme.key_chats_actionPressedBackground));
        if (Build.VERSION.SDK_INT < 21) {
            Drawable shadowDrawable = context.getResources().getDrawable(R.drawable.floating_shadow).mutate();
            shadowDrawable.setColorFilter(new PorterDuffColorFilter(0xff000000, PorterDuff.Mode.MULTIPLY));
            CombinedDrawable combinedDrawable = new CombinedDrawable(shadowDrawable, drawable, 0, 0);
            combinedDrawable.setIconSize(AndroidUtilities.dp(56), AndroidUtilities.dp(56));
            drawable = combinedDrawable;
        }
        floatingButtonContainer.setBackgroundDrawable(drawable);
        if (Build.VERSION.SDK_INT >= 21) {
            StateListAnimator animator = new StateListAnimator();
            animator.addState(new int[]{android.R.attr.state_pressed}, ObjectAnimator.ofFloat(floatingButtonIcon, "translationZ", AndroidUtilities.dp(2), AndroidUtilities.dp(4)).setDuration(200));
            animator.addState(new int[]{}, ObjectAnimator.ofFloat(floatingButtonIcon, "translationZ", AndroidUtilities.dp(4), AndroidUtilities.dp(2)).setDuration(200));
            floatingButtonContainer.setStateListAnimator(animator);
            floatingButtonContainer.setOutlineProvider(new ViewOutlineProvider() {
                @SuppressLint("NewApi")
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, AndroidUtilities.dp(56), AndroidUtilities.dp(56));
                }
            });
        }
        VerticalPositionAutoAnimator.attach(floatingButtonContainer);
        sizeNotifierFrameLayout.addView(floatingButtonContainer, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, LocaleController.isRTL ? 14 : 0, 0, LocaleController.isRTL ? 0 : 14, 14));
        floatingButtonContainer.setOnClickListener(view -> {
            if (donePressed) {
                return;
            }
            if (createCompany && editText.length() == 0) {
                Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) {
                    v.vibrate(200);
                }
                AndroidUtilities.shakeView(editText, 2, 0);
                return;
            }
            donePressed = true;

            if (createCompany) {
                IRoomsManager.getInstance().createCompany(getParentActivity(), editText.getText().toString(), new IRoomsManager.IRoomsCallback() {
                    @Override
                    public void onSuccess(String success) {
                        JSONObject jsonObject = null;
                        try {

                            jsonObject = new JSONObject(success);
                            String successfull = jsonObject.getString("success");
                            if (successfull.equals("true")) {
                                JSONObject co = jsonObject.getJSONObject("result");
                                if (co != null) {
                                    int companyId = co.getInt("id");
                                    String companyName = co.getString("name");
                                    if (PreferenceManager.getDefaultSharedPreferences(context).getString(Constants.SELECTED_COMPANY_NAME, "").equals("")) {
                                         PreferenceManager.getDefaultSharedPreferences(getParentActivity()).edit().putString(Constants.SELECTED_COMPANY_NAME, companyName).commit();
                                        PreferenceManager.getDefaultSharedPreferences(getParentActivity()).edit().putInt(Constants.SELECTED_COMPANY_ID, companyId).commit();
                                    }
                                    IRoomsManager.getInstance().addMembersToCompany(getParentActivity(), companyId, selectedContacts, new IRoomsManager.IRoomsCallback() {
                                        @Override
                                        public void onSuccess(String success) {
                                            Toast.makeText(getParentActivity(), "Company has been successfully created", Toast.LENGTH_SHORT).show();
                                            ((LaunchActivity) getParentActivity()).refreshCompany();

                                            getParentLayout().removeFragmentFromStack(getParentLayout().fragmentsStack.size() - 2);
                                            finishFragment();
                                        }

                                        @Override
                                        public void onError(String error) {
                                            try{
                                                getParentLayout().removeFragmentFromStack(getParentLayout().fragmentsStack.size() - 2);
                                                finishFragment();
                                                Toast.makeText(getParentActivity(), error, Toast.LENGTH_SHORT).show();
                                            }catch (Exception d){}

                                        }
                                    });
                                }
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onError(String error) {
                        Toast.makeText(getParentActivity(), error, Toast.LENGTH_LONG).show();
                    }
                });
            } else {
                if (action == "add") {
                    IRoomsManager.getInstance().addMembersToCompany(getParentActivity(), PreferenceManager.getDefaultSharedPreferences(getParentActivity()).getInt(Constants.SELECTED_COMPANY_ID, -1), selectedContacts, new IRoomsManager.IRoomsCallback() {
                        @Override
                        public void onSuccess(String success) {
                            Toast.makeText(getParentActivity(), "Selected members added to company", Toast.LENGTH_SHORT).show();
                            ((LaunchActivity) getParentActivity()).refreshCompany();
                            getParentLayout().removeFragmentFromStack(getParentLayout().fragmentsStack.size() - 2);
                            finishFragment();
                        }

                        @Override
                        public void onError(String error) {
                            Toast.makeText(getParentActivity(), error, Toast.LENGTH_SHORT).show();

                        }
                    });
                } else {
                    IRoomsManager.getInstance().deleteMembersFromCompany(getParentActivity(), PreferenceManager.getDefaultSharedPreferences(getParentActivity()).getInt(Constants.SELECTED_COMPANY_ID, -1), selectedContacts, new IRoomsManager.IRoomsCallback() {
                        @Override
                        public void onSuccess(String success) {
                            Toast.makeText(getParentActivity(), "Selected members deleted from company", Toast.LENGTH_SHORT).show();
                            ((LaunchActivity) getParentActivity()).refreshCompany();
                            getParentLayout().removeFragmentFromStack(getParentLayout().fragmentsStack.size() - 2);
                            finishFragment();
                        }

                        @Override
                        public void onError(String error) {
                            Toast.makeText(getParentActivity(), error, Toast.LENGTH_SHORT).show();

                        }
                    });
                }
            }


            AndroidUtilities.hideKeyboard(editText);
//            editText.setEnabled(false);
//
//            showEditDoneProgress(true);
//            reqId = MessagesController.getInstance(currentAccount).createChat(editText.getText().toString(), selectedContacts, null, chatType, currentGroupCreateLocation, currentGroupCreateAddress, AddMembersToCompany.this);

        });

        floatingButtonIcon = new ImageView(context);
        floatingButtonIcon.setScaleType(ImageView.ScaleType.CENTER);
        floatingButtonIcon.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chats_actionIcon), PorterDuff.Mode.MULTIPLY));
        floatingButtonIcon.setImageResource(R.drawable.checkbig);
        floatingButtonIcon.setPadding(0, AndroidUtilities.dp(2), 0, 0);
        floatingButtonContainer.setContentDescription(LocaleController.getString("Done", R.string.Done));
        floatingButtonContainer.addView(floatingButtonIcon, LayoutHelper.createFrame(Build.VERSION.SDK_INT >= 21 ? 56 : 60, Build.VERSION.SDK_INT >= 21 ? 56 : 60));

        progressView = new ContextProgressView(context, 1);
        progressView.setAlpha(0.0f);
        progressView.setScaleX(0.1f);
        progressView.setScaleY(0.1f);
        progressView.setVisibility(View.INVISIBLE);
        floatingButtonContainer.addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        return fragmentView;
    }

    public void setDelegate(GroupCreateFinalActivityDelegate groupCreateFinalActivityDelegate) {
        delegate = groupCreateFinalActivityDelegate;
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
    }

    @Override
    public void saveSelfArgs(Bundle args) {

        if (editText != null) {
            String text = editText.getText().toString();
            if (text != null && text.length() != 0) {
                args.putString("nameTextView", text);
            }
        }
    }

    @Override
    public void restoreSelfArgs(Bundle args) {

        String text = args.getString("nameTextView");
        if (text != null) {
            if (editText != null) {
                editText.setText(text);
            } else {
                nameToSet = text;
            }
        }
    }

    @Override
    public void onTransitionAnimationEnd(boolean isOpen, boolean backward) {
        if (isOpen) {
            editText.openKeyboard();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.updateInterfaces) {
            if (listView == null) {
                return;
            }
            int mask = (Integer) args[0];
            if ((mask & MessagesController.UPDATE_MASK_AVATAR) != 0 || (mask & MessagesController.UPDATE_MASK_NAME) != 0 || (mask & MessagesController.UPDATE_MASK_STATUS) != 0) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    if (child instanceof GroupCreateUserCell) {
                        ((GroupCreateUserCell) child).update(mask);
                    }
                }
            }
        } else if (id == NotificationCenter.chatDidFailCreate) {
            reqId = 0;
            donePressed = false;
            showEditDoneProgress(false);
            if (editText != null) {
                editText.setEnabled(true);
            }
            if (delegate != null) {
                delegate.didFailChatCreation();
            }
        } else if (id == NotificationCenter.chatDidCreated) {
            reqId = 0;
            int chat_id = (Integer) args[0];
            if (delegate != null) {
                delegate.didFinishChatCreation(this, chat_id);
            } else {
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.closeChats);
                Bundle args2 = new Bundle();
                args2.putInt("chat_id", chat_id);
                presentFragment(new ChatActivity(args2), true);
            }

        }
    }

    private void showEditDoneProgress(final boolean show) {
        if (floatingButtonIcon == null) {
            return;
        }
        if (doneItemAnimation != null) {
            doneItemAnimation.cancel();
        }
        doneItemAnimation = new AnimatorSet();
        if (show) {
            progressView.setVisibility(View.VISIBLE);
            floatingButtonContainer.setEnabled(false);
            doneItemAnimation.playTogether(
                    ObjectAnimator.ofFloat(floatingButtonIcon, "scaleX", 0.1f),
                    ObjectAnimator.ofFloat(floatingButtonIcon, "scaleY", 0.1f),
                    ObjectAnimator.ofFloat(floatingButtonIcon, "alpha", 0.0f),
                    ObjectAnimator.ofFloat(progressView, "scaleX", 1.0f),
                    ObjectAnimator.ofFloat(progressView, "scaleY", 1.0f),
                    ObjectAnimator.ofFloat(progressView, "alpha", 1.0f));
        } else {
            floatingButtonIcon.setVisibility(View.VISIBLE);
            floatingButtonContainer.setEnabled(true);
            doneItemAnimation.playTogether(
                    ObjectAnimator.ofFloat(progressView, "scaleX", 0.1f),
                    ObjectAnimator.ofFloat(progressView, "scaleY", 0.1f),
                    ObjectAnimator.ofFloat(progressView, "alpha", 0.0f),
                    ObjectAnimator.ofFloat(floatingButtonIcon, "scaleX", 1.0f),
                    ObjectAnimator.ofFloat(floatingButtonIcon, "scaleY", 1.0f),
                    ObjectAnimator.ofFloat(floatingButtonIcon, "alpha", 1.0f));

        }
        doneItemAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (doneItemAnimation != null && doneItemAnimation.equals(animation)) {
                    if (!show) {
                        progressView.setVisibility(View.INVISIBLE);
                    } else {
                        floatingButtonIcon.setVisibility(View.INVISIBLE);
                    }
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                if (doneItemAnimation != null && doneItemAnimation.equals(animation)) {
                    doneItemAnimation = null;
                }
            }
        });
        doneItemAnimation.setDuration(150);
        doneItemAnimation.start();
    }

    public class GroupCreateAdapter extends RecyclerListView.SelectionAdapter {

        private Context context;
        private int usersStartRow;

        public GroupCreateAdapter(Context ctx) {
            context = ctx;
        }

        @Override
        public int getItemCount() {
            int count = 2 + selectedContacts.size();
            if (currentGroupCreateAddress != null) {
                count += 3;
            }
            return count;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 3;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0: {
                    view = new ShadowSectionCell(context);
                    Drawable drawable = Theme.getThemedDrawable(context, R.drawable.greydivider_top, Theme.key_windowBackgroundGrayShadow);
                    CombinedDrawable combinedDrawable = new CombinedDrawable(new ColorDrawable(Theme.getColor(Theme.key_windowBackgroundGray)), drawable);
                    combinedDrawable.setFullsize(true);
                    view.setBackgroundDrawable(combinedDrawable);
                    break;
                }
                case 1:
                    HeaderCell headerCell = new HeaderCell(context);
                    headerCell.setHeight(46);
                    view = headerCell;
                    break;
                case 2:
                    view = new GroupCreateUserCell(context, false, 3, false);
                    break;
                case 3:
                default:
                    view = new TextSettingsCell(context);
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case 1: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (currentGroupCreateAddress != null && position == 1) {
                        cell.setText(LocaleController.getString("AttachLocation", R.string.AttachLocation));
                    } else {
                        cell.setText(LocaleController.formatPluralString("Members", selectedContacts.size()));
                    }
                    break;
                }
                case 2: {
                    GroupCreateUserCell cell = (GroupCreateUserCell) holder.itemView;
                    TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(selectedContacts.get(position - usersStartRow));
                    cell.setObject(user, null, null);
                    break;
                }
                case 3: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    cell.setText(currentGroupCreateAddress, false);
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (currentGroupCreateAddress != null) {
                if (position == 0) {
                    return 0;
                } else if (position == 1) {
                    return 1;
                } else if (position == 2) {
                    return 3;
                } else {
                    position -= 3;
                }
                usersStartRow = 5;
            } else {
                usersStartRow = 2;
            }
            switch (position) {
                case 0:
                    return 0;
                case 1:
                    return 1;
                case 2:
                default:
                    return 2;
            }
        }

        @Override
        public void onViewRecycled(RecyclerView.ViewHolder holder) {
            if (holder.getItemViewType() == 2) {
                ((GroupCreateUserCell) holder.itemView).recycle();
            }
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
            if (listView != null) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    if (child instanceof GroupCreateUserCell) {
                        ((GroupCreateUserCell) child).update(0);
                    }
                }
            }
        };

        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundWhite));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollActive));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollInactive));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_FASTSCROLL, null, null, null, null, Theme.key_fastScrollText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(editText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(editText, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_groupcreate_hintText));
        themeDescriptions.add(new ThemeDescription(editText, ThemeDescription.FLAG_CURSORCOLOR, null, null, null, null, Theme.key_groupcreate_cursor));
        themeDescriptions.add(new ThemeDescription(editText, ThemeDescription.FLAG_BACKGROUNDFILTER, null, null, null, null, Theme.key_windowBackgroundWhiteInputField));
        themeDescriptions.add(new ThemeDescription(editText, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_DRAWABLESELECTEDSTATE, null, null, null, null, Theme.key_windowBackgroundWhiteInputFieldActivated));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER | ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{GroupCreateUserCell.class}, new String[]{"textView"}, null, null, null, Theme.key_groupcreate_sectionText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{GroupCreateUserCell.class}, new String[]{"statusTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{GroupCreateUserCell.class}, new String[]{"statusTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{GroupCreateUserCell.class}, null, Theme.avatarDrawables, cellDelegate, Theme.key_avatar_text));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));

        themeDescriptions.add(new ThemeDescription(progressView, 0, null, null, null, null, Theme.key_contextProgressInner2));
        themeDescriptions.add(new ThemeDescription(progressView, 0, null, null, null, null, Theme.key_contextProgressOuter2));

        themeDescriptions.add(new ThemeDescription(editText, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(editText, ThemeDescription.FLAG_HINTTEXTCOLOR, null, null, null, null, Theme.key_windowBackgroundWhiteHintText));

        return themeDescriptions;
    }
}
