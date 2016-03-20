package org.rm3l.ddwrt.mgmt.register.steps;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.FragmentActivity;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
import com.google.common.base.Strings;

import org.apache.commons.io.IOUtils;
import org.codepond.wizardroid.WizardStep;
import org.codepond.wizardroid.persistence.ContextVariable;
import org.rm3l.ddwrt.R;
import org.rm3l.ddwrt.utils.ReportingUtils;
import org.rm3l.ddwrt.utils.Utils;

import java.io.IOException;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.rm3l.ddwrt.utils.DDWRTCompanionConstants.MAX_PRIVKEY_SIZE_BYTES;
import static org.rm3l.ddwrt.utils.Utils.openKeyboard;
import static org.rm3l.ddwrt.utils.Utils.toHumanReadableByteCount;

/**
 * Created by rm3l on 15/03/16.
 */
public class RouterConnectionDetailsStep extends WizardStep {

    private static final String LOG_TAG = RouterConnectionDetailsStep.class.getSimpleName();

    private static final int READ_REQUEST_CODE = 42;

    private View rootView;

    @ContextVariable
    private String username;

    private EditText usernameEt;

    @ContextVariable
    private String port;

    private EditText portEt;

    @ContextVariable
    private String connectionProtocol;

    private Spinner connectionProtocolView;

    @ContextVariable
    private String password;

    private EditText pwdView;

    @ContextVariable
    private Integer checkedAuthMethodRadioButtonId;

    private RadioGroup authMethodRg;

    @ContextVariable
    private String privkeyErrorMsg;
    @ContextVariable
    private String privkeyPath;
    private TextView privkeyErrorMsgView;
    private TextView privkeyPathView;
    @ContextVariable
    private String privkeyButtonHint;
    private Button privkeyButtonView;

    //Wire the layout to the step
    public RouterConnectionDetailsStep() {
    }

    //Set your layout here
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        rootView = inflater.inflate(
                R.layout.wizard_add_router_2_router_connection_details_step, container, false);

        usernameEt = (EditText) rootView.findViewById(R.id.router_add_username);

        portEt = (EditText) rootView.findViewById(R.id.router_add_port);

        connectionProtocolView = (Spinner) rootView.findViewById(R.id.router_add_proto);

        authMethodRg = (RadioGroup) rootView.findViewById(R.id.router_add_ssh_auth_method);

        authMethodRg.check(checkedAuthMethodRadioButtonId != null ?
                checkedAuthMethodRadioButtonId : R.id.router_add_ssh_auth_method_password);

        pwdView = (EditText) rootView.findViewById(R.id.router_add_password);
        final CheckBox pwdShowCheckBox = (CheckBox) rootView.findViewById(R.id.router_add_password_show_checkbox);

        pwdShowCheckBox
                .setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (!isChecked) {
                            pwdView.setInputType(
                                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                            pwdView.requestFocus();
                        } else {
                            pwdView.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                            pwdView.requestFocus();
                        }
                        pwdView.setSelection(pwdView.length());
                    }
                });

        privkeyErrorMsgView = (TextView) rootView.findViewById(R.id.router_add_privkey_error_msg);

        privkeyErrorMsgView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (TextUtils.isEmpty(s)) {
                    privkeyErrorMsgView.setVisibility(View.GONE);
                } else {
                    privkeyErrorMsgView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        final View privkeyHdrView = rootView.findViewById(R.id.router_add_privkey_hdr);
        privkeyButtonView = (Button) rootView.findViewById(R.id.router_add_privkey);
        privkeyPathView = (TextView) rootView.findViewById(R.id.router_add_privkey_path);
//                        final EditText pwdView = (EditText) rootView.findViewById(R.id.router_add_password);
//                        final CheckBox pwdShowCheckBox = (CheckBox) rootView.findViewById(R.id.router_add_password_show_checkbox);
        final TextInputLayout pwdInputLayout =
                (TextInputLayout) rootView.findViewById(R.id.router_add_password_input_layout);

        ((RadioGroup) rootView.findViewById(R.id.router_add_ssh_auth_method))
                .setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {

                    @Override
                    public void onCheckedChanged(RadioGroup group, int checkedId) {

                        switch (checkedId) {
                            case R.id.router_add_ssh_auth_method_none:
                                privkeyPathView.setText(null);
                                privkeyHdrView.setVisibility(View.GONE);
                                privkeyButtonView.setVisibility(View.GONE);
                                pwdView.setText(null);
                                pwdView.setVisibility(View.GONE);
                                pwdShowCheckBox.setVisibility(View.GONE);
                                pwdInputLayout.setErrorEnabled(false);
                                validateFields();
                                break;
                            case R.id.router_add_ssh_auth_method_password:
                                privkeyPathView.setText(null);
                                privkeyHdrView.setVisibility(View.GONE);
                                privkeyButtonView.setVisibility(View.GONE);
                                privkeyErrorMsgView.setText(null);
                                pwdView.setVisibility(View.VISIBLE);
                                pwdView.setHint("e.g., 'default' (may be empty) ");
                                pwdShowCheckBox.setVisibility(View.VISIBLE);
                                break;
                            case R.id.router_add_ssh_auth_method_privkey:
                                pwdView.setText(null);
                                privkeyButtonView.setHint(getString(R.string.router_add_path_to_privkey));
                                pwdView.setVisibility(View.VISIBLE);
                                pwdView.setHint("Key passphrase, if applicable");
                                pwdShowCheckBox.setVisibility(View.VISIBLE);
                                privkeyHdrView.setVisibility(View.VISIBLE);
                                privkeyButtonView.setVisibility(View.VISIBLE);
                                pwdInputLayout.setErrorEnabled(false);
                                break;
                            default:
                                break;
                        }
                    }
                });

        privkeyButtonView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
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

                RouterConnectionDetailsStep.this.startActivityForResult(intent, READ_REQUEST_CODE);
            }
        });


        //and set default values by using Context Variables
        usernameEt.setText(username != null ? username : "root");
        portEt.setText(port != null ? port : "22");
        if (password != null) {
            pwdView.setText(password);
        }

        if (privkeyButtonHint != null) {
            privkeyButtonView.setHint(privkeyButtonHint);
        }
        privkeyPathView.setText(privkeyPath);
        privkeyErrorMsgView.setText(privkeyErrorMsg);

        //TODO Set Spinner

        final TextWatcher textWatcherFormValidator = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                validateFields();
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        };
        final View.OnFocusChangeListener focusChangeListenerFormValidator = new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                validateFields();
            }
        };
        portEt.setOnFocusChangeListener(focusChangeListenerFormValidator);
        usernameEt.setOnFocusChangeListener(focusChangeListenerFormValidator);
        pwdView.setOnFocusChangeListener(focusChangeListenerFormValidator);
        privkeyPathView.setOnFocusChangeListener(focusChangeListenerFormValidator);
        privkeyPathView.addTextChangedListener(textWatcherFormValidator);

        return rootView;
    }

    /**
     * Called whenever the wizard proceeds to the next step or goes back to the previous step
     */

    @Override
    public void onExit(int exitCode) {
        switch (exitCode) {
            case WizardStep.EXIT_NEXT:
                bindDataFields();
                break;
            case WizardStep.EXIT_PREVIOUS:
                //Do nothing...
                break;
        }
    }

    private void bindDataFields() {
        //TODO Do some work
        //...
        //The values of these fields will be automatically stored in the wizard context
        //and will be populated in the next steps only if the same field names are used.
        username = usernameEt.getText().toString();
        password = pwdView.getText().toString();
        port = portEt.getText().toString();
        checkedAuthMethodRadioButtonId = authMethodRg.getCheckedRadioButtonId();
        privkeyErrorMsg = privkeyErrorMsgView.getText().toString();
        privkeyPath = privkeyPathView.getText().toString();
        privkeyButtonHint = privkeyButtonView.getHint().toString();
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
                Crashlytics.log(Log.INFO, LOG_TAG, "Uri: " + uri.toString());
                Cursor uriCursor = null;

                try {
                    final ContentResolver contentResolver = this.getActivity().getContentResolver();

                    if (contentResolver == null || (uriCursor =
                            contentResolver.query(uri, null, null, null, null)) == null) {
                        privkeyErrorMsgView.setText(
                                "Unknown Content Provider - please select a different location or auth method!");
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
                        privkeyErrorMsgView.setText(String
                                .format("File '%s' too big (%s). Limit is %s", filename,
                                        toHumanReadableByteCount(fileSize),
                                        toHumanReadableByteCount(MAX_PRIVKEY_SIZE_BYTES)));
                        return;
                    }

                    //Replace button hint message with file name
                    final CharSequence fileSelectorOriginalHint = privkeyButtonView.getHint();
                    if (!Strings.isNullOrEmpty(filename)) {
                        privkeyButtonView.setHint(filename);
                    }

                    //Set file actual content in hidden field
                    try {
                        privkeyPathView.setText(IOUtils.toString(contentResolver.openInputStream(uri)));
                    } catch (IOException e) {
                        e.printStackTrace();
                        privkeyErrorMsgView.setText("Error: " + e.getMessage());
                        privkeyButtonView.setHint(fileSelectorOriginalHint);
                    }
                } finally {
                    if (uriCursor != null) {
                        try {
                            uriCursor.close();
                        } catch (final Exception e) {
                            e.printStackTrace();
                            ReportingUtils.reportException(null, e);
                        }
                    }
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, resultData);
    }

    private void validateFields() {
        final FragmentActivity activity = getActivity();
        final ScrollView contentScrollView = (ScrollView) rootView
                .findViewById(R.id.router_add_content_scroll_view);

        boolean validPort;
        final TextInputLayout portInputLayout =
                (TextInputLayout) rootView.findViewById(R.id.router_add_port_input_layout);
        try {
            final String portStr = portEt.getText().toString();
            validPort = (!isNullOrEmpty(portStr) && (Integer.parseInt(portStr) > 0));
        } catch (@NonNull final Exception e) {
            e.printStackTrace();
            validPort = false;
        }
        portInputLayout.setErrorEnabled(!validPort);
        if (!validPort) {
            portInputLayout.setError(getString(R.string.router_add_port_invalid));
            Utils.scrollToView(contentScrollView, portEt);
            portEt.requestFocus();
            openKeyboard(activity, portEt);
            notifyIncomplete();
            return;
        }
        portInputLayout.setErrorEnabled(false);

        final TextInputLayout sshLoginInputLayout =
                (TextInputLayout) rootView.findViewById(R.id.router_add_username_input_layout);
        if (isNullOrEmpty(usernameEt.getText().toString())) {
            sshLoginInputLayout.setErrorEnabled(true);
            sshLoginInputLayout.setError(getString(R.string.router_add_username_invalid));
            Utils.scrollToView(contentScrollView, usernameEt);
            usernameEt.requestFocus();
            openKeyboard(activity, usernameEt);
            notifyIncomplete();
            return;
        }
        sshLoginInputLayout.setErrorEnabled(false);

        final int checkedAuthMethodRadioButtonId = authMethodRg.getCheckedRadioButtonId();
        switch (checkedAuthMethodRadioButtonId) {
            case R.id.router_add_ssh_auth_method_password: {
                //Check password
                final TextInputLayout pwdInputLayout =
                        (TextInputLayout) rootView.findViewById(R.id.router_add_password_input_layout);
                if (isNullOrEmpty(pwdView.getText().toString())) {
                    pwdInputLayout.setErrorEnabled(true);
                    pwdInputLayout.setError("Password required");
                    Utils.scrollToView(contentScrollView, pwdView);
                    pwdView.requestFocus();
                    openKeyboard(activity, pwdView);
                    notifyIncomplete();
                    return;
                }
                pwdInputLayout.setErrorEnabled(false);
            }
                break;
            case R.id.router_add_ssh_auth_method_privkey: {
                //Check privkey
                if (isNullOrEmpty(privkeyPathView.getText().toString())) {
                    privkeyErrorMsgView.setText(getString(R.string.router_add_privkey_invalid));
                    privkeyButtonView.requestFocus();
                    notifyIncomplete();
                    return;
                }
            }
                break;
            default:
                break;
        }

        notifyCompleted();
    }
}
