/*
 * DD-WRT Companion is a mobile app that lets you connect to,
 * monitor and manage your DD-WRT routers on the go.
 *
 * Copyright (C) 2014  Armel Soro
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Contact Info: Armel Soro <apps+ddwrt@rm3l.org>
 */

package org.rm3l.ddwrt.mgmt;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.DhcpInfo;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.text.Editable;
import android.text.InputType;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.gms.ads.AdView;
import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.FluentIterable;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.rm3l.ddwrt.BuildConfig;
import org.rm3l.ddwrt.R;
import org.rm3l.ddwrt.exceptions.DDWRTCompanionException;
import org.rm3l.ddwrt.mgmt.dao.DDWRTCompanionDAO;
import org.rm3l.ddwrt.resources.conn.Router;
import org.rm3l.ddwrt.utils.AdUtils;
import org.rm3l.ddwrt.utils.DDWRTCompanionConstants;
import org.rm3l.ddwrt.utils.SSHUtils;
import org.rm3l.ddwrt.utils.Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import de.keyboardsurfer.android.widget.crouton.Crouton;
import de.keyboardsurfer.android.widget.crouton.Style;

import static android.widget.TextView.BufferType.EDITABLE;
import static com.google.common.base.Strings.isNullOrEmpty;
import static de.keyboardsurfer.android.widget.crouton.Style.ALERT;
import static org.rm3l.ddwrt.utils.DDWRTCompanionConstants.ALWAYS_CHECK_CONNECTION_PREF_KEY;
import static org.rm3l.ddwrt.utils.DDWRTCompanionConstants.DEFAULT_SHARED_PREFERENCES_KEY;
import static org.rm3l.ddwrt.utils.DDWRTCompanionConstants.MAX_PRIVKEY_SIZE_BYTES;
import static org.rm3l.ddwrt.utils.Utils.isDemoRouter;
import static org.rm3l.ddwrt.utils.Utils.toHumanReadableByteCount;

public abstract class AbstractRouterMgmtDialogFragment
        extends DialogFragment {

    private static final String LOG_TAG = AbstractRouterMgmtDialogFragment.class.getSimpleName();
    private static final int READ_REQUEST_CODE = 42;
    protected DDWRTCompanionDAO dao;
    protected SharedPreferences mGlobalSharedPreferences;
    private RouterMgmtDialogListener mListener;

    protected final AtomicBoolean mActivityCreatedAndInitialized = new AtomicBoolean(false);

    private Router buildRouter(AlertDialog d) throws IOException {
        final Router router = new Router();
        final String uuid = ((TextView) d.findViewById(R.id.router_add_uuid)).getText().toString();
        if (!isNullOrEmpty(uuid)) {
            router.setUuid(uuid);
        } else {
            router.setUuid(UUID.randomUUID().toString());
        }
        router.setName(((EditText) d.findViewById(R.id.router_add_name)).getText().toString());
        router.setRemoteIpAddress(((EditText) d.findViewById(R.id.router_add_ip)).getText().toString());
        router.setRemotePort(Integer.parseInt(((EditText) d.findViewById(R.id.router_add_port)).getText().toString()));
        router.setRouterConnectionProtocol(Router.RouterConnectionProtocol.valueOf(
                (((Spinner) d.findViewById(R.id.router_add_proto))).getSelectedItem().toString()
        ));
        final int pos = (((Spinner) d.findViewById(R.id.router_add_firmware))).getSelectedItemPosition();
        final String[] fwStringArray = d.getContext().getResources().getStringArray(R.array.router_firmwares_array_values);
        if (fwStringArray != null && pos < fwStringArray.length) {
            final String fwSelection = fwStringArray[pos];
            if (!"auto".equals(fwSelection)) {
                router.setRouterFirmware(fwSelection);
            } // else we will try to guess
        } // else we will try to guess

        router.setUsername(((EditText) d.findViewById(R.id.router_add_username)).getText().toString(), true);
        router.setStrictHostKeyChecking(((CheckBox) d.findViewById(R.id.router_add_is_strict_host_key_checking)).isChecked());

        final String password = ((EditText) d.findViewById(R.id.router_add_password)).getText().toString();
        final String privkey = ((TextView) d.findViewById(R.id.router_add_privkey_path)).getText().toString();
        if (!isNullOrEmpty(password)) {
            router.setPassword(password, true);
        }
        if (!isNullOrEmpty(privkey)) {

//            //Convert privkey into a format accepted by JSCh
            //Causes a build issue with SpongyCastle
//            final PEMParser pemParser = new PEMParser(new StringReader(privkey));
//            Object object = pemParser.readObject();
//            PEMDecryptorProvider decProv = new JcePEMDecryptorProviderBuilder().build(nullToEmpty(password).toCharArray());
//            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("SC");
//            KeyPair kp;
//            if (object instanceof PEMEncryptedKeyPair) {
//                Log.d(LOG_TAG, "Encrypted key - we will use provided password");
//                kp = converter.getKeyPair(((PEMEncryptedKeyPair) object).decryptKeyPair(decProv));
//            } else {
//                Log.d(LOG_TAG, "Unencrypted key - no password needed");
//                kp = converter.getKeyPair((PEMKeyPair) object);
//            }
//            final PrivateKey privateKey = \"fake-key\";
//            StringWriter stringWriter = new StringWriter();
//            JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter);
//            pemWriter.writeObject(privateKey);
//            pemWriter.close();

            router.setPrivKey(privkey, true);
        }

        final FragmentActivity activity = getActivity();
        router.setUseLocalSSIDLookup(activity,
                ((CheckBox) d.findViewById(R.id.router_add_local_ssid_lookup)).isChecked());
        router.setFallbackToPrimaryAddr(activity,
                ((CheckBox) d.findViewById(R.id.router_add_fallback_to_primary)).isChecked());

        final Splitter splitter = Splitter.on("\n").omitEmptyStrings();

        //Now build SSID data
        final LinearLayout container = (LinearLayout) d.findViewById(R.id.router_add_local_ssid_container);
        final int childCount = container.getChildCount();
        final List<Router.LocalSSIDLookup> lookups = new ArrayList<>();
        for (int i = 0; i < childCount; i++){
            final View view = container.getChildAt(i);
            if (!(view instanceof TextView)) {
                continue;
            }
            final String textViewString = ((TextView) view).getText().toString();
            final List<String> strings = splitter.splitToList(textViewString);
            if (strings.size() < 3) {
                continue;
            }
            final Router.LocalSSIDLookup localSSIDLookup = new Router.LocalSSIDLookup();
            localSSIDLookup.setNetworkSsid(strings.get(0));
            localSSIDLookup.setReachableAddr(strings.get(1));
            try {
                localSSIDLookup.setPort(Integer.parseInt(strings.get(2)));
            } catch (final Exception e) {
                Utils.reportException(e);
                localSSIDLookup.setPort(22); //default SSH port
            }
            lookups.add(localSSIDLookup);
        }
        router.setLocalSSIDLookupData(activity, lookups);

        return router;
    }

    protected abstract CharSequence getDialogMessage();

    @Nullable
    protected abstract CharSequence getDialogTitle();

    protected abstract CharSequence getPositiveButtonMsg();

    protected abstract void onPositiveButtonActionSuccess(@NonNull RouterMgmtDialogListener mListener, @Nullable Router router, boolean error);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        this.dao = RouterManagementActivity.getDao(getActivity());

        mGlobalSharedPreferences = getActivity()
                .getSharedPreferences(DEFAULT_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE);

    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final FragmentActivity activity = getActivity();

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);

        // Get the layout inflater
        final LayoutInflater inflater = activity.getLayoutInflater();
        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        final View view = inflater.inflate(R.layout.activity_router_add, null);

        final ScrollView contentScrollView = (ScrollView) view.findViewById(R.id.router_add_scrollview);

        ((Spinner) view.findViewById(R.id.router_add_proto)).setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // An item was selected. You can retrieve the selected item using
                // parent.getItemAtPosition(position)
                //Since there is only one connection method for now, we won't do anything, but we may display only the relevant
                //form items, and hide the others.
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        ((Spinner) view.findViewById(R.id.router_add_firmware)).setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View childView, int pos, long id) {
                //FIXME Once we support other types of Router firmwares
//                final View ddwrtInstructionsView = view.findViewById(R.id.router_add_ddwrt_instructions);
//                final View openwrtInstructionsView = view.findViewById(R.id.router_add_openwrt_instructions);
//                switch (pos) {
//                    case 1:
//                        //DD-WRT
//                        ddwrtInstructionsView
//                                .setVisibility(View.VISIBLE);
//                        openwrtInstructionsView
//                                .setVisibility(View.GONE);
//                        break;
//                    case 2:
//                        //OpenWrt
//                        ddwrtInstructionsView
//                                .setVisibility(View.GONE);
//                        openwrtInstructionsView
//                                .setVisibility(View.VISIBLE);
//                        break;
//                    default:
//                        ddwrtInstructionsView
//                                .setVisibility(View.GONE);
//                        openwrtInstructionsView
//                                .setVisibility(View.GONE);
//                        break;
//                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        final EditText pwdView = (EditText) view.findViewById(R.id.router_add_password);
        final CheckBox pwdShowCheckBox = (CheckBox) view.findViewById(R.id.router_add_password_show_checkbox);

        pwdShowCheckBox
                .setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (!isChecked) {
//                        pwdView.setTransformationMethod(
//                                PasswordTransformationMethod.getInstance());
                            pwdView.setInputType(
                                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                            Utils.scrollToView(contentScrollView, pwdView);
                            pwdView.requestFocus();
                        } else {
//                        pwdView.setTransformationMethod(
//                                HideReturnsTransformationMethod.getInstance());
                            pwdView.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                            Utils.scrollToView(contentScrollView, pwdView);
                            pwdView.requestFocus();
                        }
                        pwdView.setSelection(pwdView.length());
                    }
                });


        ((RadioGroup) view.findViewById(R.id.router_add_ssh_auth_method))
                .setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

                    @Override
                    public void onCheckedChanged(RadioGroup group, int checkedId) {
                        final View privkeyHdrView = view.findViewById(R.id.router_add_privkey_hdr);
                        final Button privkeyView = (Button) view.findViewById(R.id.router_add_privkey);
                        final TextView privkeyPathView = (TextView) view.findViewById(R.id.router_add_privkey_path);
                        final TextView pwdHdrView = (TextView) view.findViewById(R.id.router_add_password_hdr);
//                        final EditText pwdView = (EditText) view.findViewById(R.id.router_add_password);
//                        final CheckBox pwdShowCheckBox = (CheckBox) view.findViewById(R.id.router_add_password_show_checkbox);

                        switch (checkedId) {
                            case R.id.router_add_ssh_auth_method_none:
                                privkeyPathView.setText(null);
                                privkeyHdrView.setVisibility(View.GONE);
                                privkeyView.setVisibility(View.GONE);
                                pwdHdrView.setVisibility(View.GONE);
                                pwdView.setText(null);
                                pwdView.setVisibility(View.GONE);
                                pwdShowCheckBox.setVisibility(View.GONE);
                                break;
                            case R.id.router_add_ssh_auth_method_password:
                                privkeyPathView.setText(null);
                                privkeyHdrView.setVisibility(View.GONE);
                                privkeyView.setVisibility(View.GONE);
                                pwdHdrView.setText("Password");
                                pwdHdrView.setVisibility(View.VISIBLE);
                                pwdView.setVisibility(View.VISIBLE);
                                pwdView.setHint("e.g., 'default' (may be empty) ");
                                pwdShowCheckBox.setVisibility(View.VISIBLE);
                                break;
                            case R.id.router_add_ssh_auth_method_privkey:
                                pwdView.setText(null);
                                privkeyView.setHint(getString(R.string.router_add_path_to_privkey));
                                pwdHdrView.setText("Passphrase (if applicable)");
                                pwdHdrView.setVisibility(View.VISIBLE);
                                pwdView.setVisibility(View.VISIBLE);
                                pwdView.setHint("Key passphrase, if applicable");
                                pwdShowCheckBox.setVisibility(View.VISIBLE);
                                privkeyHdrView.setVisibility(View.VISIBLE);
                                privkeyView.setVisibility(View.VISIBLE);
                                break;
                            default:
                                break;
                        }
                    }
                });

        //Advanced options
        final TextView advancedOptionsButton = (TextView) view.findViewById(R.id.router_add_advanced_options_button);
        final View advancedOptionsView = view.findViewById(R.id.router_add_advanced_options);
        advancedOptionsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (advancedOptionsView.getVisibility() == View.VISIBLE) {
                    advancedOptionsView.setVisibility(View.GONE);
                    advancedOptionsButton
                            .setCompoundDrawablesWithIntrinsicBounds(
                                    R.drawable.ic_action_hardware_keyboard_arrow_right, 0, 0, 0);
                    Utils.scrollToView(contentScrollView, advancedOptionsView);
                    advancedOptionsView.requestFocus();
                } else {
                    advancedOptionsView.setVisibility(View.VISIBLE);
                    advancedOptionsButton
                            .setCompoundDrawablesWithIntrinsicBounds(
                                    R.drawable.ic_action_hardware_keyboard_arrow_down, 0, 0, 0);
                    Utils.scrollToView(contentScrollView, advancedOptionsButton);
                    advancedOptionsButton.requestFocus();
                }
            }
        });

        //Local SSID Lookup Checkbox
        final CheckBox useLocalSsidLookupCheckbox = (CheckBox) view.findViewById(R.id.router_add_local_ssid_lookup);
        final FloatingActionButton addLocalSsidLookupButton = (FloatingActionButton) view.findViewById(R.id.router_add_local_ssid_button);
        final CheckBox fallbackCheckbox = (CheckBox) view.findViewById(R.id.router_add_fallback_to_primary);
        useLocalSsidLookupCheckbox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                final int depsVisibility = isChecked ? View.VISIBLE : View.GONE;
                addLocalSsidLookupButton.setVisibility(depsVisibility);
//                fallbackCheckbox.setVisibility(depsVisibility);
                if (isChecked) {
                    Utils.scrollToView(contentScrollView, addLocalSsidLookupButton);
                } else {
                    fallbackCheckbox.setChecked(false);
                    Utils.scrollToView(contentScrollView, useLocalSsidLookupCheckbox);
                }
            }
        });

        final LinearLayout localSsidsContainer = (LinearLayout) view.findViewById(R.id.router_add_local_ssid_container);

        //Clicking on addLocalSsidLookupButton should show up an additional form dialog
        addLocalSsidLookupButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (BuildConfig.DONATIONS || BuildConfig.WITH_ADS) {
                    //Download the full version to unlock this version
                    Utils.displayUpgradeMessage(activity, "Add Alternate Addresses");
                    return;
                }

                final AlertDialog.Builder addLocalSsidLookupDialogBuilder = new AlertDialog.Builder(activity);
                final View addLocalSsidLookupDialogView = inflater.inflate(R.layout.activity_router_add_local_ssid_lookup, null);

                final WifiManager wifiManager = (WifiManager) getActivity().getSystemService(Context.WIFI_SERVICE);
                final AutoCompleteTextView ssidAutoCompleteView = (AutoCompleteTextView)
                        addLocalSsidLookupDialogView.findViewById(R.id.router_add_local_ssid_lookup_ssid);

                try {
                    if (wifiManager != null) {
                        final List<ScanResult> results = wifiManager.getScanResults();
                        ssidAutoCompleteView
                                .setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1,
                                        FluentIterable.from(results)
                                                .transform(new Function<ScanResult, String>() {
                                                    @Override
                                                    public String apply(@Nullable ScanResult input) {
                                                        if (input == null) {
                                                            return null;
                                                        }
                                                        return input.SSID;
                                                    }
                                                }).toArray(String.class)));
                    }
                    //Fill with current network SSID
                    String wifiName = Utils.getWifiName(activity);
                    if (wifiName != null && wifiName.startsWith("\"") && wifiName.endsWith("\"")) {
                        wifiName = wifiName.substring(1, wifiName.length() - 1);
                    }
                    ssidAutoCompleteView.setText(wifiName, EDITABLE);
                } catch (final Exception e) {
                    e.printStackTrace();
                    //No worries
                }

                final EditText ipEditText = (EditText) addLocalSsidLookupDialogView.findViewById(R.id.router_add_local_ssid_lookup_ip);
                //Fill with network gateway IP
                try {
                    if (wifiManager != null) {
                        final DhcpInfo dhcpInfo = wifiManager.getDhcpInfo();
                        if (dhcpInfo != null) {
                            ipEditText.setText(Utils.intToIp(dhcpInfo.gateway), EDITABLE);
                        }
                    }
                } catch (final Exception e) {
                    e.printStackTrace();
                    //No worries
                }

                final EditText portEditText = (EditText) addLocalSsidLookupDialogView.findViewById(R.id.router_add_local_ssid_lookup_port);
                final EditText primaryPort = (EditText) view.findViewById(R.id.router_add_port);
                if (primaryPort != null) {
                    portEditText.setText(primaryPort.getText(), EDITABLE);
                }

                final AlertDialog addLocalSsidLookupDialog = addLocalSsidLookupDialogBuilder
                        .setTitle("Add Local SSID Lookup")
                        .setMessage("This allows you to define an alternate IP or DNS name to use for this router, " +
                                "when connected to a network with the specified name.\n" +
                                "For example, you may want to set a local IP address when connected to your home network, " +
                                "and by default use an external DNS name. " +
                                "This would speed up router data retrieval from the app when at home.")
                        .setView(addLocalSsidLookupDialogView)
                        .setCancelable(true)
                        .setPositiveButton("Add", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                //Do nothing here because we override this button later to change the close behaviour.
                                //However, we still need this because on older versions of Android unless we
                                //pass a handler the button doesn't get instantiated
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                //Cancelled - nothing more to do!
                            }
                        })
                        .create();

                addLocalSsidLookupDialog.show();

                addLocalSsidLookupDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final String ssidText = ssidAutoCompleteView.getText().toString();
                        //Validate fields
                        if (isNullOrEmpty(ssidText)) {
                            //displayMessage and prevent exiting
                            Crouton.makeText(activity,
                                    "Invalid Network Name",
                                    Style.ALERT,
                                    (ViewGroup) view.findViewById(R.id.router_add_local_ssid_lookup_notification_viewgroup))
                                    .show();
                            ssidAutoCompleteView.requestFocus();
                            openKeyboard(ssidAutoCompleteView);
                            return;
                        }
                        final String ipEditTextText = ipEditText.getText().toString();
                        if (!(Patterns.IP_ADDRESS.matcher(ipEditTextText).matches()
                                || Patterns.DOMAIN_NAME.matcher(ipEditTextText).matches())) {
                            //displayMessage and prevent exiting
                            Crouton.makeText(activity,
                                    "Invalid IP or DNS Name",
                                    Style.ALERT,
                                    (ViewGroup) view.findViewById(R.id.router_add_local_ssid_lookup_notification_viewgroup))
                                    .show();
                            ipEditText.requestFocus();
                            openKeyboard(ipEditText);
                            return;
                        }
                        boolean validPort;
                        final String portStr = portEditText.getText().toString();
                        try {
                            validPort = (!isNullOrEmpty(portStr) && (Integer.parseInt(portStr) > 0));
                        } catch (@NonNull final Exception e) {
                            e.printStackTrace();
                            validPort = false;
                        }
                        if (!validPort) {
                            portEditText.requestFocus();
                            openKeyboard(portEditText);
                        }

                        final TextView localSsidView = new TextView(activity);
                        localSsidView.setText(ssidText + "\n" +
                                ipEditTextText + "\n" + portStr
                        );
                        localSsidView.setLayoutParams(new ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT));
                        localSsidView
                                .setCompoundDrawablesWithIntrinsicBounds(
                                        0, 0, android.R.drawable.ic_menu_close_clear_cancel, 0);

                        localSsidView.setOnTouchListener(new View.OnTouchListener() {
                            @Override
                            public boolean onTouch(View v, MotionEvent event) {
                                final int DRAWABLE_RIGHT = 2;

                                if (event.getAction() == MotionEvent.ACTION_UP) {
                                    if (event.getRawX() >= (localSsidView.getRight() -
                                            localSsidView.getCompoundDrawables()[DRAWABLE_RIGHT].getBounds().width())) {

                                        //Remove view from container layout
                                        final ViewParent parent = localSsidView.getParent();
                                        if (parent instanceof LinearLayout) {
                                            ((LinearLayout)parent).removeView(localSsidView);
                                        }
                                    }
                                }
                                return true;
                            }
                        });

                        localSsidsContainer.addView(localSsidView);
                        final View lineView = Utils.getLineView(activity);
                        if (lineView != null) {
                            localSsidsContainer.addView(lineView);
                        }

                        addLocalSsidLookupDialog.dismiss();
                    }
                });
            }
        });

        builder
                .setMessage(this.getDialogMessage())
                .setView(view)
                        // Add action buttons
                .setPositiveButton(this.getPositiveButtonMsg(), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        //Do nothing here because we override this button later to change the close behaviour.
                        //However, we still need this because on older versions of Android unless we
                        //pass a handler the button doesn't get instantiated
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        AbstractRouterMgmtDialogFragment.this.getDialog().cancel();
                    }
                });
        if (!(this.getDialogTitle() == null || this.getDialogTitle().toString().isEmpty())) {
            builder.setTitle(this.getDialogTitle());
        }
        return builder.create();
    }

    /**
     * Receive the result from a previous call to
     * {@link #startActivityForResult(android.content.Intent, int)}.  This follows the
     * related Activity API as described there in
     * {@link android.app.Activity#onActivityResult(int, int, android.content.Intent)}.
     *
     * @param requestCode The integer request code originally supplied to
     *                    startActivityForResult(), allowing you to identify who this
     *                    result came from.
     * @param resultCode  The integer result code returned by the child activity
     *                    through its setResult().
     * @param resultData  An Intent, which can return result data to the caller
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent resultData) {
        // The ACTION_OPEN_DOCUMENT intent was sent with the request code
        // READ_REQUEST_CODE. If the request code seen here doesn't match, it's the
        // response to some other intent, and the code below shouldn't run at all.

        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // The document selected by the user won't be returned in the intent.
            // Instead, a URI to that document will be contained in the return intent
            // provided to this method as a parameter.
            // Pull that URI using resultData.getData().
            Uri uri;
            if (resultData != null) {
                uri = resultData.getData();
                Log.i(LOG_TAG, "Uri: " + uri.toString());
                final AlertDialog d = (AlertDialog) getDialog();
                if (d != null) {
                    Cursor uriCursor = null;

                    try {
                        final ContentResolver contentResolver = this.getActivity().getContentResolver();

                        if (contentResolver == null || (uriCursor =
                                contentResolver.query(uri, null, null, null, null)) == null) {
                            displayMessage("Unknown Content Provider - please select a different location or auth method!",
                                    ALERT);
                            return;
                        }

                        /*
                         * Get the column indexes of the data in the Cursor,
                         * move to the first row in the Cursor, get the data,
                         * and display it.
                         */
                        final int nameIndex = uriCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        final int sizeIndex = uriCursor.getColumnIndex(OpenableColumns.SIZE);

                        uriCursor.moveToFirst();

                        //File size in bytes
                        final long fileSize = uriCursor.getLong(sizeIndex);
                        final String filename = uriCursor.getString(nameIndex);

                        //Check file size
                        if (fileSize > MAX_PRIVKEY_SIZE_BYTES) {
                            displayMessage(String
                                    .format("File '%s' too big (%s). Limit is %s", filename,
                                            toHumanReadableByteCount(fileSize),
                                            toHumanReadableByteCount(MAX_PRIVKEY_SIZE_BYTES)), ALERT);
                            return;
                        }

                        //Replace button hint message with file name
                        final Button fileSelectorButton = (Button) d.findViewById(R.id.router_add_privkey);
                        final CharSequence fileSelectorOriginalHint = fileSelectorButton.getHint();
                        if (!Strings.isNullOrEmpty(filename)) {
                            fileSelectorButton.setHint(filename);
                        }

                        //Set file actual content in hidden field
                        final TextView privKeyPath = (TextView) d.findViewById(R.id.router_add_privkey_path);
                        try {
                            privKeyPath.setText(IOUtils.toString(contentResolver.openInputStream(uri)));
                        } catch (IOException e) {
                            displayMessage("Error: " + e.getMessage(), ALERT);
                            e.printStackTrace();
                            fileSelectorButton.setHint(fileSelectorOriginalHint);
                        }
                    } finally {
                        if (uriCursor != null) {
                            try {
                                uriCursor.close();
                            } catch (final Exception e) {
                                e.printStackTrace();
                                Utils.reportException(e);
                            }
                        }
                    }
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, resultData);
    }

    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the NoticeDialogListener so we can send events to the host
            mListener = (RouterMgmtDialogListener) activity;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(activity.toString()
                    + " must implement NoticeDialogListener");
        }
    }

    @Override
    public void onStart() {
        super.onStart();    //super.onStart() is where dialog.show() is actually called on the underlying dialog, so we have to do it after this point

        if (!mActivityCreatedAndInitialized.get()) {
            final AlertDialog d = (AlertDialog) getDialog();
            if (d != null) {

                final TextView demoTextView = (TextView) d.findViewById(R.id.router_add_ip_demo_text);
                demoTextView.setText(
                        demoTextView.getText().toString()
                                .replaceAll("%PACKAGE_NAME%", BuildConfig.APPLICATION_ID));

                final View ddwrtInstructionsView = d.findViewById(R.id.router_add_ddwrt_instructions);
                final View ddwrtInstructionsWithAds = d.findViewById(R.id.router_add_ddwrt_instructions_ads);

                d.getWindow().setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.MATCH_PARENT);

                if (BuildConfig.WITH_ADS) {
                    //For Ads to show up, otherwise we get the following error message:
                    //Not enough space to show ad. Needs 320x50 dp, but only has 288x597 dp.

                    //Also Display shorte.st link to instructions (monetized)
                    //FIXME Fix when support of other firmwares is in place
                    ddwrtInstructionsView.setVisibility(View.GONE);
                    ddwrtInstructionsWithAds.setVisibility(View.VISIBLE);
                } else {
                    //FIXME Fix when support of other firmwares is in place
                    ddwrtInstructionsView.setVisibility(View.VISIBLE);
                    ddwrtInstructionsWithAds.setVisibility(View.GONE);
                }

                AdUtils.buildAndDisplayAdViewIfNeeded(d.getContext(),
                        (AdView) d.findViewById(R.id.activity_router_add_adView));

                d.findViewById(R.id.router_add_privkey).setOnClickListener(new View.OnClickListener() {
                    @TargetApi(Build.VERSION_CODES.KITKAT)
                    @Override
                    public void onClick(View view) {
                        //Open up file picker

                        // ACTION_OPEN_DOCUMENT is the intent to choose a file via the system's file
                        // browser.
                        final Intent intent = new Intent();

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                            intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
                        } else {
                            intent.setAction(Intent.ACTION_GET_CONTENT);
                        }

                        // Filter to only show results that can be "opened", such as a
                        // file (as opposed to a list of contacts or timezones)
                        intent.addCategory(Intent.CATEGORY_OPENABLE);

                        // search for all documents available via installed storage providers
                        intent.setType("*/*");

                        AbstractRouterMgmtDialogFragment.this.startActivityForResult(intent, READ_REQUEST_CODE);
                    }
                });

                d.getButton(Dialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        //Validate form
                        boolean validForm = validateForm(d);

                        if (validForm) {
                            // Now check actual connection to router ...
                            new CheckRouterConnectionAsyncTask(
                                    ((EditText) d.findViewById(R.id.router_add_ip)).getText().toString(),
                                    getActivity().getSharedPreferences(DEFAULT_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
                                            .getBoolean(ALWAYS_CHECK_CONNECTION_PREF_KEY, true))
                                    .execute(d);
                        }
                        ///else dialog stays open. 'Cancel' button can still close it.
                    }
                });
            }
        }
    }

    @Nullable
    private Router doCheckConnectionToRouter(@NonNull AlertDialog d) throws Exception {
        final Router router = buildRouter(d);

        if (isDemoRouter(router)) {
            router.setName(DDWRTCompanionConstants.DEMO);
            return router;
        }

        //This will throw an exception if connection could not be established!
        SSHUtils.checkConnection(getActivity(), mGlobalSharedPreferences, router, 10000);

        return router;
    }

    private boolean validateForm(@NonNull AlertDialog d) {
        final EditText ipAddrView = (EditText) d.findViewById(R.id.router_add_ip);

        final Editable ipAddrViewText = ipAddrView.getText();

        final ScrollView contentScrollView = (ScrollView) d.findViewById(R.id.router_add_scrollview);

        if (isDemoRouter(ipAddrViewText.toString())) {
            //Skip validation
            return true;
        }

        if (!(Patterns.IP_ADDRESS.matcher(ipAddrViewText).matches()
                || Patterns.DOMAIN_NAME.matcher(ipAddrViewText).matches())) {
            displayMessage(getString(R.string.router_add_dns_or_ip_invalid) + ":" + ipAddrViewText,
                    ALERT);
            Utils.scrollToView(contentScrollView, ipAddrView);
            ipAddrView.requestFocus();
            openKeyboard(ipAddrView);
            return false;
        }

        boolean validPort;
        final EditText portView = (EditText) d.findViewById(R.id.router_add_port);
        try {
            final String portStr = portView.getText().toString();
            validPort = (!isNullOrEmpty(portStr) && (Integer.parseInt(portStr) > 0));
        } catch (@NonNull final Exception e) {
            e.printStackTrace();
            validPort = false;
        }
        if (!validPort) {
            displayMessage(getString(R.string.router_add_port_invalid) + ":" + portView.getText(), ALERT);
            Utils.scrollToView(contentScrollView, portView);

            portView.requestFocus();
            openKeyboard(portView);
            return false;
        }

        final EditText sshUsernameView = (EditText) d.findViewById(R.id.router_add_username);
        if (isNullOrEmpty(sshUsernameView.getText().toString())) {
            displayMessage(getString(R.string.router_add_username_invalid), ALERT);
            Utils.scrollToView(contentScrollView, sshUsernameView);
            sshUsernameView.requestFocus();
            openKeyboard(sshUsernameView);
            return false;
        }

        final int checkedAuthMethodRadioButtonId = ((RadioGroup) d.findViewById(R.id.router_add_ssh_auth_method)).getCheckedRadioButtonId();
        if (checkedAuthMethodRadioButtonId == R.id.router_add_ssh_auth_method_password) {
            //Check password
            final EditText sshPasswordView = (EditText) d.findViewById(R.id.router_add_password);
            if (isNullOrEmpty(sshPasswordView.getText().toString())) {
                displayMessage(getString(R.string.router_add_password_invalid), ALERT);
                Utils.scrollToView(contentScrollView, sshPasswordView);

                sshPasswordView.requestFocus();
                openKeyboard(sshPasswordView);
                return false;
            }
        } else if (checkedAuthMethodRadioButtonId == R.id.router_add_ssh_auth_method_privkey) {
            //Check privkey
            final TextView sshPrivKeyView = (TextView) d.findViewById(R.id.router_add_privkey_path);
            if (isNullOrEmpty(sshPrivKeyView.getText().toString())) {
                displayMessage(getString(R.string.router_add_privkey_invalid), ALERT);
                Utils.scrollToView(contentScrollView, sshPrivKeyView);

                sshPrivKeyView.requestFocus();
                return false;
            }
        }

        return true;
    }

    private void openKeyboard(final TextView mTextView) {
        final InputMethodManager imm = (InputMethodManager)
                getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            // only will trigger it if no physical keyboard is open
            imm.showSoftInput(mTextView, 0);
        }
    }

    private void displayMessage(final String msg, final Style style) {
        if (isNullOrEmpty(msg)) {
            return;
        }
        final AlertDialog d = (AlertDialog) getDialog();
        Crouton.makeText(getActivity(), msg, style, (ViewGroup) (d == null ? getView() : d.findViewById(R.id.router_add_notification_viewgroup))).show();
    }

    @Nullable
    protected abstract Router onPositiveButtonClickHandler(@NonNull final Router router);

    protected class CheckRouterConnectionAsyncTask extends AsyncTask<AlertDialog, Void, CheckRouterConnectionAsyncTask.CheckRouterConnectionAsyncTaskResult<Router>> {

        private final String routerIpOrDns;
        private AlertDialog checkingConnectionDialog = null;
        private boolean checkActualConnection;

        public CheckRouterConnectionAsyncTask(String routerIpOrDns, boolean checkActualConnection) {
            this.routerIpOrDns = routerIpOrDns;
            //Disabling 'checkActualConnection' setting, as we are now trying to detect the firmware used
//            this.checkActualConnection = checkActualConnection;
            this.checkActualConnection = true;
        }

        @Override
        protected void onPreExecute() {
            if (checkActualConnection) {
                checkingConnectionDialog = Utils.buildAlertDialog(getActivity(), null,
                        String.format("Hold on - checking connection to router '%s'...", routerIpOrDns), false, false);
                checkingConnectionDialog.show();
            }
        }

        @Nullable
        @Override
        protected CheckRouterConnectionAsyncTask.CheckRouterConnectionAsyncTaskResult<Router> doInBackground(AlertDialog... dialogs) {
            if (!checkActualConnection) {
                try {
                    return new CheckRouterConnectionAsyncTaskResult<>(buildRouter(dialogs[0]), null);
                } catch (IOException e) {
                    e.printStackTrace();
                    //No worries, as we should not check actual connection
                    return new CheckRouterConnectionAsyncTaskResult<>(null, e);
                }
            }
            Router result = null;
            Exception exception = null;
            try {
                result = doCheckConnectionToRouter(dialogs[0]);
            } catch (Exception e) {
                e.printStackTrace();
                exception = e;
            }

            return new CheckRouterConnectionAsyncTask.CheckRouterConnectionAsyncTaskResult<>(result, exception);
        }

        @Override
        protected void onPostExecute(@NonNull CheckRouterConnectionAsyncTask.CheckRouterConnectionAsyncTaskResult<Router> result) {
            if (checkingConnectionDialog != null) {
                checkingConnectionDialog.cancel();
            }

            final Exception e = result.getException();
            Router router = result.getResult();
            if (e != null) {
                final Throwable rootCause = Throwables.getRootCause(e);
                displayMessage(getString(R.string.router_add_connection_unsuccessful) +
                        ": " + (rootCause != null ? rootCause.getMessage() : e.getMessage()), Style.ALERT);
                if (rootCause != null &&
                        (rootCause instanceof IOException) &&
                        StringUtils.containsIgnoreCase(rootCause.getMessage(), "End of IO Stream Read")) {
                    //Common issue with some routers
                    Utils.buildAlertDialog(getActivity(),
                            "SSH Error: End of IO Stream Read",
                            "Some firmware builds (like DD-WRT r21061) reportedly have non-working SSH server versions (e.g., 'dropbear_2013.56'). \n" +
                                    "This might be the cause of this error. \n" +
                                    "Make sure you can manually SSH into the router from a computer, using the same credentials you provided to the app. \n" +
                                    "If the error persists, we recommend you upgrade or downgrade your router to a build " +
                                    "with a working SSH server, then try again.\n\n" +
                                    "Please reach out to us at apps@rm3l.org for assistance.\n" +
                                    "Sorry for the inconvenience.",
                            true, true).show();
                }
                Utils.reportException(
                        new DDWRTCompanionExceptionForConnectionChecksException(
                                router != null ?
                                        router.toString() : e.getMessage(), e));
            } else {
                if (router != null) {
                    AlertDialog daoAlertDialog = null;
                    try {
                        //Register or update router
                        daoAlertDialog = Utils.buildAlertDialog(getActivity(), null,
                                String.format("Registering (or updating) router '%s'...", routerIpOrDns), false, false);
                        daoAlertDialog.show();
                        router = AbstractRouterMgmtDialogFragment.this.onPositiveButtonClickHandler(router);
                        dismiss();
                    } finally {
                        if (daoAlertDialog != null) {
                            daoAlertDialog.cancel();
                        }
                    }
                } else {
                    displayMessage(getString(R.string.router_add_internal_error), Style.ALERT);
                }
            }

            if (AbstractRouterMgmtDialogFragment.this.mListener != null) {
                AbstractRouterMgmtDialogFragment.this.onPositiveButtonActionSuccess(
                        AbstractRouterMgmtDialogFragment.this.mListener, router, e != null);
            }

        }

        @Override
        protected void onCancelled(CheckRouterConnectionAsyncTask.CheckRouterConnectionAsyncTaskResult<Router> router) {
            super.onCancelled(router);
            if (checkingConnectionDialog != null) {
                checkingConnectionDialog.cancel();
            }
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            if (checkingConnectionDialog != null) {
                checkingConnectionDialog.cancel();
            }
        }

        class CheckRouterConnectionAsyncTaskResult<T> {
            private final T result;
            private final Exception exception;

            private CheckRouterConnectionAsyncTaskResult(T result, Exception exception) {
                this.result = result;
                this.exception = exception;
            }

            public T getResult() {
                return result;
            }

            public Exception getException() {
                return exception;
            }
        }

    }

    private class DDWRTCompanionExceptionForConnectionChecksException extends DDWRTCompanionException {
        private DDWRTCompanionExceptionForConnectionChecksException(@Nullable String detailMessage, @Nullable Throwable throwable) {
            super(detailMessage, throwable);
        }
    }
}
