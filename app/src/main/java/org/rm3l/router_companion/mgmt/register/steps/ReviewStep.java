package org.rm3l.router_companion.mgmt.register.steps;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.FragmentActivity;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
import com.google.common.base.Splitter;
import com.google.common.base.Throwables;
import com.google.gson.Gson;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.codepond.wizardroid.Wizard;
import org.codepond.wizardroid.WizardStep;
import org.rm3l.router_companion.R;
import org.rm3l.router_companion.exceptions.DDWRTCompanionException;
import org.rm3l.router_companion.mgmt.RouterManagementActivity;
import org.rm3l.router_companion.mgmt.dao.DDWRTCompanionDAO;
import org.rm3l.router_companion.mgmt.register.ManageRouterWizard;
import org.rm3l.router_companion.mgmt.register.resources.RouterWizardAction;
import org.rm3l.router_companion.resources.Encrypted;
import org.rm3l.router_companion.resources.conn.Router;
import org.rm3l.router_companion.service.tasks.RouterInfoForFeedbackServiceTask;
import org.rm3l.router_companion.service.tasks.RouterModelUpdaterServiceTask;
import org.rm3l.router_companion.RouterCompanionAppConstants;
import org.rm3l.router_companion.utils.ReportingUtils;
import org.rm3l.router_companion.utils.SSHUtils;
import org.rm3l.router_companion.utils.Utils;
import org.rm3l.router_companion.utils.snackbar.SnackbarUtils;
import org.rm3l.router_companion.widgets.wizard.MaterialWizard;
import org.rm3l.router_companion.widgets.wizard.MaterialWizardStep;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.rm3l.router_companion.RouterCompanionAppConstants.DEFAULT_SHARED_PREFERENCES_KEY;
import static org.rm3l.router_companion.utils.Utils.isDemoRouter;

/**
 * Created by rm3l on 21/03/16.
 */
public class ReviewStep extends MaterialWizardStep {

    private static final String TAG = ReviewStep.class.getSimpleName();

    private final Gson gson = MaterialWizard.GSON_BUILDER.create();

    private String uuid;
    private TextView uuidView;

    private String routerName;
    private TextView routerNameView;

    private String routerIpOrDns;
    private TextView routerIpOrDnsView;

    private String routerFirmware;
    private TextView routerFirmwareView;

    private String connectionProtocol;
    private TextView connectionProtocolView;

    private String port;
    private TextView portView;

    private String username;
    private TextView usernameView;

    private String authMethod;
    private TextView authMethodView;

    private TextView authMethodHidden;
    private String password;
    private EditText passwordView;

    private String privkeyButtonHint;
    private TextView privkeyButtonHintView;

    private String privkeyContent;

    private boolean useLocalSSIDLookup;
    private TextView useLocalSSIDLookupView;

    private List<Router.LocalSSIDLookup> lookups;

    private Router router;

    private DDWRTCompanionDAO dao;
    private int action;
    private Router routerSelected;

    //Wire the layout to the step
    public ReviewStep() {
    }

    @Override
    public String getWizardStepTitle() {
        return "Review your changes";
    }

    //Set your layout here
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        final Context context = getContext();
        this.dao = RouterManagementActivity.getDao(context);

        final Object tag = container.getTag();
        if (tag != null) {
            try {
                final RouterWizardAction routerWizardAction = RouterWizardAction.GSON_BUILDER.create()
                        .fromJson(tag.toString(), RouterWizardAction.class);
                this.routerSelected = dao.getRouter(routerWizardAction.getRouterUuid());
                this.action = routerWizardAction.getAction();
            } catch (final Exception e) {
                //No worries
                e.printStackTrace();
            }
        }
        Crashlytics.log(Log.DEBUG, TAG,
                "<routerSelected=" + routerSelected + ",action=" + action + ">");

        final View v = inflater.inflate(
                R.layout.wizard_manage_router_4_review,
                container, false);

        uuidView = (TextView) v.findViewById(R.id.wizard_add_router_review_router_uuid);
        routerNameView = (TextView) v.findViewById(R.id.wizard_add_router_review_router_name);
        routerIpOrDnsView = (TextView) v.findViewById(R.id.wizard_add_router_review_router_ip_dns);
        routerFirmwareView = (TextView) v.findViewById(R.id.wizard_add_router_review_router_firmware);
        connectionProtocolView = (TextView) v.findViewById(R.id.wizard_add_router_review_router_conn_proto);
        portView = (TextView) v.findViewById(R.id.wizard_add_router_review_router_conn_proto_ssh_port);
        usernameView = (TextView) v.findViewById(R.id.wizard_add_router_review_router_conn_proto_ssh_username);
        authMethodView = (TextView) v.findViewById(R.id.wizard_add_router_review_ssh_auth_method);
        authMethodHidden = (TextView) v.findViewById(R.id.wizard_add_router_review_ssh_auth_method_hidden);
        privkeyButtonHintView = (TextView) v.findViewById(R.id.wizard_add_router_review_ssh_auth_method_privkey_path);
        passwordView = (EditText) v.findViewById(R.id.wizard_add_router_review_ssh_auth_method_password_value);
        useLocalSSIDLookupView = (TextView) v.findViewById(R.id.wizard_add_router_review_use_local_ssid_lookup_yes_no);

        final CheckBox showPasswordCheckBox = (CheckBox) v.findViewById(R.id.wizard_add_router_review_password_show_checkbox);
        showPasswordCheckBox
                .setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (!isChecked) {
                            passwordView.setInputType(
                                    InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                        } else {
                            passwordView.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                        }
                        passwordView.setEnabled(false);
                    }
                });

        authMethodView.setVisibility(View.VISIBLE);

        authMethodHidden.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                final String valueString = s.toString();
                try {
                    final TextView privkeyHdrView = (TextView)
                            v.findViewById(R.id.wizard_add_router_review_ssh_auth_method_privkey_hdr);
                    final TextView passwordHdrView = (TextView)
                            v.findViewById(R.id.wizard_add_router_review_ssh_auth_method_password_hdr);
                    switch (Integer.parseInt(valueString)) {
                        case Router.SSHAuthenticationMethod_PASSWORD: {
                            authMethodView.setText("Password");
                            privkeyHdrView.setVisibility(View.GONE);
                            privkeyButtonHintView.setText(null);
                            privkeyButtonHintView.setVisibility(View.GONE);

                            passwordHdrView.setVisibility(View.VISIBLE);
                            passwordView.setText(password);
                            passwordView.setVisibility(View.VISIBLE);
                            showPasswordCheckBox.setVisibility(View.VISIBLE);
                        }
                            break;
                        case Router.SSHAuthenticationMethod_PUBLIC_PRIVATE_KEY: {
                            authMethodView.setText("Private Key");
                            privkeyHdrView.setVisibility(View.VISIBLE);
                            privkeyButtonHintView.setText(privkeyButtonHint);
                            privkeyButtonHintView.setVisibility(View.VISIBLE);

                            final Map wizardContext = MaterialWizard.getWizardContext(getContext());
                            final Object passwordObj = wizardContext.get("password");
                            final String unencryptedPassword;
                            if (passwordObj == null || (unencryptedPassword =
                                    Encrypted.d(passwordObj.toString())) == null
                                    || unencryptedPassword.isEmpty()) {
                                passwordHdrView.setVisibility(View.GONE);
                                passwordView.setVisibility(View.GONE);
                                passwordView.setText(null);
                                showPasswordCheckBox.setVisibility(View.GONE);
                            } else {
                                passwordHdrView.setVisibility(View.VISIBLE);
                                passwordView.setVisibility(View.VISIBLE);
                                passwordView.setText(unencryptedPassword);
                                showPasswordCheckBox.setVisibility(View.VISIBLE);
                            }
                        }
                            break;
                        case Router.SSHAuthenticationMethod_NONE:
                        default: {
                            authMethodView.setText("None");
                            privkeyHdrView.setVisibility(View.GONE);
                            privkeyButtonHintView.setText(null);
                            privkeyButtonHintView.setVisibility(View.GONE);
                            passwordHdrView.setVisibility(View.GONE);
                            passwordView.setVisibility(View.GONE);
                            passwordView.setText(null);
                            showPasswordCheckBox.setVisibility(View.GONE);
                        }
                        break;
                    }
                } catch (final NumberFormatException nfe) {
                    nfe.printStackTrace();
                }
            }
        });

        if (!isViewShown) {
            loadFromWizardContext();
        }

        return v;
    }

    @Override
    protected void onVisibleToUser() {
        //Load from context
        loadFromWizardContext();
        if (isViewShown) {
            uuidView.setText(uuid);
            routerNameView.setText(isNullOrEmpty(routerName) ? "-" : routerName);

            if (Utils.isDemoRouter(routerIpOrDns)) {
                routerIpOrDnsView.setText(RouterCompanionAppConstants.DEMO);
            } else {
                routerIpOrDnsView.setText(isNullOrEmpty(routerIpOrDns) ? "-" : routerIpOrDns);
            }

            routerFirmwareView.setText(isNullOrEmpty(routerFirmware) ? "-" : routerFirmware );
            connectionProtocolView.setText(isNullOrEmpty(connectionProtocol) ? "-" : connectionProtocol );
            portView.setText(isNullOrEmpty(port) ? "-" : port );
            usernameView.setText(isNullOrEmpty(username) ? "-" : username );
            passwordView.setText(isNullOrEmpty(password) ? "-" : password );
            privkeyButtonHintView.setText(isNullOrEmpty(privkeyButtonHint) ? "-" : privkeyButtonHint );
            Crashlytics.log(Log.DEBUG, TAG, "authMethod: [" + authMethod + "]");
            if (authMethod != null) {
                authMethodHidden.setText(authMethod);
            }

            if (Utils.isDemoRouter(routerIpOrDns)) {
                useLocalSSIDLookupView.setText("N/A");
            } else {
                String useLocalSSIDText = StringUtils.capitalize(
                        BooleanUtils.toStringYesNo(useLocalSSIDLookup).toLowerCase());
                if (useLocalSSIDLookup) {
                    useLocalSSIDText += (" (" + lookups.size() + " lookup entries)");
                }
                useLocalSSIDLookupView.setText(useLocalSSIDText);
            }
        }
    }

    private void loadFromWizardContext() {
        final Map wizardContext = MaterialWizard.getWizardContext(getContext());

        final Object uuidObj = wizardContext.get("uuid");
        uuid = uuidObj != null ? uuidObj.toString() : "-";

        final Object routerNameObj = wizardContext.get("routerName");
        routerName = routerNameObj != null ? routerNameObj.toString() : "-";

        final Object routerIpOrDnsObj = wizardContext.get("routerIpOrDns");
        routerIpOrDns = routerIpOrDnsObj != null ? routerIpOrDnsObj.toString() : "-";

        final Object routerFirmwareObj = wizardContext.get("routerFirmware");
        routerFirmware = routerFirmwareObj != null ? routerFirmwareObj.toString() : "-";

        final Object connectionProtocolObj = wizardContext.get("connectionProtocol");
        connectionProtocol = connectionProtocolObj != null ?
                connectionProtocolObj.toString() : "-";

        final Object portObj = wizardContext.get("port");
        port = portObj != null ? portObj.toString() : "-";

        final Object usernameObj = wizardContext.get("username");
        username = usernameObj != null ? usernameObj.toString() : "-";

        final Object passwordObj = wizardContext.get("password");
        password = passwordObj != null ? Encrypted.d(passwordObj.toString()) : null;

        final Object privkeyButtonHintObj = wizardContext.get("privkeyButtonHint");
        privkeyButtonHint = privkeyButtonHintObj != null ? privkeyButtonHintObj.toString() : null;

        final Object privkeyContentObj = wizardContext.get("privkeyPath");
        if (privkeyContentObj != null) {
            privkeyContent = privkeyContentObj.toString();
        }

        final Object authMethodObj = wizardContext.get("authMethod");
        Crashlytics.log(Log.DEBUG, TAG, "authMethodObj: " + authMethodObj);
        if (authMethodObj != null) {
            try {
                authMethod = authMethodObj.toString();
            } catch (final NumberFormatException e) {
                e.printStackTrace();
            }
        }

        final Object useLocalSSIDLookupObj = wizardContext.get("useLocalSSIDLookup");
        if (useLocalSSIDLookupObj != null) {
            useLocalSSIDLookup = BooleanUtils.toBoolean(useLocalSSIDLookupObj.toString());
        }

        final Object localSSIDLookupDetailsObj = wizardContext.get("localSSIDLookupDetails");
        lookups = new ArrayList<>();
        if (localSSIDLookupDetailsObj != null) {
            final String localSSIDLookupDetailsStr = localSSIDLookupDetailsObj.toString();
            final List list = gson.fromJson(localSSIDLookupDetailsStr, List.class);
            if (list != null) {
                final Splitter splitter = Splitter.on("\n").omitEmptyStrings();
                for (final Object obj : list) {
                    if (obj == null) {
                        continue;
                    }
                    final List<String> strings = splitter.splitToList(obj.toString());
                    if (strings.size() >= 3) {
                        final Router.LocalSSIDLookup localSSIDLookup = new Router.LocalSSIDLookup();
                        localSSIDLookup.setNetworkSsid(strings.get(0));
                        localSSIDLookup.setReachableAddr(strings.get(1));
                        try {
                            localSSIDLookup.setPort(Integer.parseInt(strings.get(2)));
                        } catch (final Exception e) {
                            ReportingUtils.reportException(null, e);
                            localSSIDLookup.setPort(22); //default SSH port
                        }
                        lookups.add(localSSIDLookup);
                    }
                }
            }
        }
    }

    private Router buildRouter()  {
        final Router router = new Router(getContext());
        if (routerSelected != null &&
                action != RouterWizardAction.ADD &&
                action != RouterWizardAction.COPY) {
            router.setUuid(routerSelected.getUuid());
        } else {
            router.setUuid(UUID.randomUUID().toString());
        }
        router.setName(routerName);
        router.setRemoteIpAddress(routerIpOrDns);
        router.setRemotePort(port != null ? Integer.parseInt(port) : 22);
        router.setRouterConnectionProtocol(
                connectionProtocol != null ?
                        Router.RouterConnectionProtocol.valueOf(connectionProtocol) :
                        Router.RouterConnectionProtocol.SSH);
        //FIXME To change when we will support other formwares
        router.setRouterFirmware(Router.RouterFirmware.DDWRT);
//        final int pos = (((Spinner) d.findViewById(R.id.router_add_firmware))).getSelectedItemPosition();
//        final String[] fwStringArray = d.getContext().getResources().getStringArray(R.array.router_firmwares_array_values);
//        if (fwStringArray != null && pos < fwStringArray.length) {
//            final String fwSelection = fwStringArray[pos];
//            if (!"auto".equals(fwSelection)) {
//                router.setRouterFirmware(fwSelection);
//            } // else we will try to guess
//        } // else we will try to guess

        router.setUsername(username, true);
//        router.setStrictHostKeyChecking(((CheckBox) d.findViewById(R.id.router_add_is_strict_host_key_checking)).isChecked());

//        final String password = ((EditText) d.findViewById(R.id.router_add_password)).getText().toString();
//        final String privkey = ((TextView) d.findViewById(R.id.router_add_privkey_path)).getText().toString();
        if (!isNullOrEmpty(password)) {
            router.setPassword(password, true);
        }
        if (!isNullOrEmpty(privkeyContent)) {

//            //Convert privkey into a format accepted by JSCh
            //Causes a build issue with SpongyCastle
//            final PEMParser pemParser = new PEMParser(new StringReader(privkey));
//            Object object = pemParser.readObject();
//            PEMDecryptorProvider decProv = new JcePEMDecryptorProviderBuilder().build(nullToEmpty(password).toCharArray());
//            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("SC");
//            KeyPair kp;
//            if (object instanceof PEMEncryptedKeyPair) {
//                Crashlytics.log(Log.DEBUG, LOG_TAG, "Encrypted key - we will use provided password");
//                kp = converter.getKeyPair(((PEMEncryptedKeyPair) object).decryptKeyPair(decProv));
//            } else {
//                Crashlytics.log(Log.DEBUG, LOG_TAG, "Unencrypted key - no password needed");
//                kp = converter.getKeyPair((PEMKeyPair) object);
//            }
//            final PrivateKey privateKey = \"fake-key\";
//            StringWriter stringWriter = new StringWriter();
//            JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter);
//            pemWriter.writeObject(privateKey);
//            pemWriter.close();

            router.setPrivKey(privkeyContent, true);
        }

        final FragmentActivity activity = getActivity();
        router.setUseLocalSSIDLookup(activity,
                useLocalSSIDLookup);
        router.setFallbackToPrimaryAddr(activity,
                !useLocalSSIDLookup);

        if (!isDemoRouter(router) && lookups != null && !lookups.isEmpty()) {
            router.setLocalSSIDLookupData(activity, lookups);
        }

        return router;
    }

    @Override
    protected void onExitNext() {
        final Router dbRouter;
        if (routerSelected != null &&
                action != RouterWizardAction.ADD &&
                action != RouterWizardAction.COPY) {
            dbRouter = this.dao.updateRouter(router);
        } else {
            //This is a new router to add
            dbRouter = this.dao.insertRouter(router);
        }
        if (dbRouter != null) {
            final Context context = getContext();
            context.getSharedPreferences(
                    DEFAULT_SHARED_PREFERENCES_KEY,
                    Context.MODE_PRIVATE).edit()
                    .putString(ManageRouterWizard.class.getSimpleName(), dbRouter.getUuid())
                    .apply();
            Utils.requestBackup(context);
        }
    }

    @Override
    public Boolean validateStep(Wizard wizard) {
        router = buildRouter();
        boolean checkActualConnection = true;
        if (routerSelected != null &&
                action != RouterWizardAction.ADD &&
                action != RouterWizardAction.COPY) {
            //This is an update - check whether any of the connection parameters have changed
            if (StringUtils.equals(routerSelected.getRemoteIpAddress(), router.getRemoteIpAddress()) &&
                    routerSelected.getRouterFirmware() == router.getRouterFirmware() &&
                    routerSelected.getRouterConnectionProtocol() == router.getRouterConnectionProtocol() &&
                    routerSelected.getRemotePort() == router.getRemotePort() &&
                    StringUtils.equals(routerSelected.getUsernamePlain(), router.getUsernamePlain()) &&
                    routerSelected.getSshAuthenticationMethod() == router.getSshAuthenticationMethod()) {

                //Check actual password and privkey
                switch (routerSelected.getSshAuthenticationMethod()) {
                    case PASSWORD:
                        checkActualConnection = !StringUtils.equals(routerSelected.getPasswordPlain(),
                                router.getPasswordPlain());
                        break;
                    case PUBLIC_PRIVATE_KEY:
                        checkActualConnection = !StringUtils.equals(routerSelected.getPrivKeyPlain(),
                                router.getPrivKeyPlain());
                        break;
                    default:
                        checkActualConnection = false;
                        break;
                }
            }
        }
        new CheckRouterConnectionAsyncTask(wizard, checkActualConnection).execute();
        //We are returning null to indicate that this step will take care of updating the wizard
        return null;
    }

    @Nullable
    private Router doCheckConnectionToRouter() throws Exception {
        if (isDemoRouter(router)) {
//            router.setName(DDWRTCompanionConstants.DEMO);
            return router;
        }

        //This will throw an exception if connection could not be established!
        SSHUtils.checkConnection(getActivity(),
                getContext().getSharedPreferences(
                        DEFAULT_SHARED_PREFERENCES_KEY,
                        Context.MODE_PRIVATE), router, 10000);

        return router;
    }

    public class CheckRouterConnectionAsyncTask extends AsyncTask<Void, Void, CheckRouterConnectionAsyncTask.CheckRouterConnectionAsyncTaskResult<Router>> {

        private final Wizard wizard;
        private AlertDialog checkingConnectionDialog = null;
        private boolean checkActualConnection;

        public CheckRouterConnectionAsyncTask(Wizard wizard, boolean checkActualConnection) {
            this.wizard = wizard;
            this.checkActualConnection = checkActualConnection;
        }

        @Override
        protected void onPreExecute() {
            if (isAdded() && checkActualConnection) {
                checkingConnectionDialog = Utils.buildAlertDialog(getActivity(), null,
                        String.format("Hold on - checking connection to router '%s'...",
                                router.getRemoteIpAddress()), false, false);
                checkingConnectionDialog.show();
            }
        }

        @Nullable
        @Override
        protected CheckRouterConnectionAsyncTask.CheckRouterConnectionAsyncTaskResult<Router> doInBackground(Void... voids) {
            if (!(isAdded() && checkActualConnection)) {
                return new CheckRouterConnectionAsyncTaskResult<>(router, null);
            }
            Router result = null;
            Exception exception = null;
            try {
                result = doCheckConnectionToRouter();
                if (!Utils.isDemoRouter(router)) {
                    if (isAdded()) {
                        final Context context = getContext();
                        new RouterModelUpdaterServiceTask(context).runBackgroundServiceTask(router);
                        new RouterInfoForFeedbackServiceTask(context).runBackgroundServiceTask(router);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                exception = e;
            }

            return new CheckRouterConnectionAsyncTask.CheckRouterConnectionAsyncTaskResult<>(result, exception);
        }

        @Override
        protected void onPostExecute(@NonNull CheckRouterConnectionAsyncTask.CheckRouterConnectionAsyncTaskResult<Router> result) {
            if (!isAdded()) {
                Crashlytics.log(Log.WARN, TAG, "Fragment no longer attached to activity");
                this.cancel(true);
            }
            if (checkingConnectionDialog != null) {
                checkingConnectionDialog.cancel();
            }

            final Exception e = result.getException();
            Router router = result.getResult();
            if (e != null) {
                final Throwable rootCause = Throwables.getRootCause(e);
                SnackbarUtils.buildSnackbar(getContext(),
                        getView(), Color.RED,
                        getString(R.string.router_add_connection_unsuccessful) +
                                ": " + (rootCause != null ? rootCause.getMessage() : e.getMessage()),
                        Color.WHITE,
                        null, Color.YELLOW,
                        Snackbar.LENGTH_LONG, null, null, true);
//                Utils.buildAlertDialog(getActivity(), "Error", getString(R.string.router_add_connection_unsuccessful) +
//                        ": " + (rootCause != null ? rootCause.getMessage() : e.getMessage()), true, true).show();
                if (StringUtils.containsIgnoreCase(
                        e.getMessage(),
                        "End of IO Stream Read")
                        ||
                        (rootCause != null &&
//                          (rootCause instanceof IOException) &&
                                StringUtils.containsIgnoreCase(
                                        rootCause.getMessage(),
                                        "End of IO Stream Read"))) {
                    //Common issue with some routers
                    Utils.buildAlertDialog(getActivity(),
                            "SSH Error: End of IO Stream Read",
                            "Some firmware builds (like DD-WRT r21061) reportedly have non-working SSH server versions " +
                                    "(e.g., 'dropbear_2013.56'). \n" +
                                    "This might be the cause of this error. \n" +
                                    "Make sure you can manually SSH into the router from a computer, " +
                                    "using the same credentials you provided to the app. \n" +
                                    "If the error persists, we recommend you upgrade or downgrade " +
                                    "your router to a build " +
                                    "with a working SSH server, then try again.\n\n" +
                                    "Please reach out to us at apps@rm3l.org for assistance.",
                            true, true).show();
                }
                ReportingUtils.reportException(
                        null, new DDWRTCompanionExceptionForConnectionChecksException(
                                router != null ?
                                        router.toString() : e.getMessage(), e));
            } else {
                if (wizard == null) {
                    Crashlytics.logException(new IllegalStateException("wizard == NULL"));
                } else {
                    onExitSynchronous(WizardStep.EXIT_NEXT);
                    wizard.goNext();
                }
            }
        }

        @Override
        protected void onCancelled(CheckRouterConnectionAsyncTask.CheckRouterConnectionAsyncTaskResult<Router> router) {
            super.onCancelled(router);
            if (isAdded() && checkingConnectionDialog != null) {
                checkingConnectionDialog.cancel();
            }
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            if (isAdded() && checkingConnectionDialog != null) {
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
