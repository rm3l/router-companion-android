package org.rm3l.ddwrt.tasker.bundle;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.twofortyfouram.spackle.AppBuildInfo;

import net.jcip.annotations.ThreadSafe;

import org.rm3l.ddwrt.tasker.BuildConfig;
import org.rm3l.ddwrt.tasker.Constants;
import org.rm3l.ddwrt.tasker.ui.activity.EditActivity;

import static com.twofortyfouram.assertion.Assertions.assertNotNull;


/**
 * Manages the {@link com.twofortyfouram.locale.api.Intent#EXTRA_BUNDLE EXTRA_BUNDLE} for this
 * plug-in.
 */
@ThreadSafe
public final class PluginBundleValues {

    public static final String BUNDLE_PREFIX = BuildConfig.APPLICATION_ID + ".extra.";
    public static final String BUNDLE_OUTPUT_VARIABLE_NAME = BUNDLE_PREFIX + "OUTPUT.VARIABLE_NAME";
    public static final String BUNDLE_OUTPUT_IS_VARIABLE = BUNDLE_PREFIX + "OUTPUT.IS_VARIABLE";
    public static final String BUNDLE_COMMAND_SUPPORTED_PARAM = BUNDLE_PREFIX + "COMMAND.SUPPORTED_PARAM";
    public static final String BUNDLE_COMMAND_SUPPORTED_PARAM_HINT = BUNDLE_PREFIX + "COMMAND.SUPPORTED_PARAM_HINT";
    public static final String BUNDLE_COMMAND_SUPPORTED_READABLE_NAME = BUNDLE_PREFIX + "COMMAND.SUPPORTED_READABLE_NAME";
    public static final String BUNDLE_COMMAND_SUPPORTED_NAME = BUNDLE_PREFIX + "COMMAND.SUPPORTED_NAME";
    public static final String BUNDLE_COMMAND_CUSTOM_CMD = BUNDLE_PREFIX + "COMMAND.CUSTOM.CMD";
    public static final String BUNDLE_COMMAND_CUSTOM_VARIABLE_NAME = BUNDLE_PREFIX + "COMMAND.CUSTOM.VARIABLE_NAME";
    public static final String BUNDLE_COMMAND_CUSTOM_IS_VARIABLE = BUNDLE_PREFIX + "COMMAND.CUSTOM.IS_VARIABLE";
    public static final String BUNDLE_COMMAND_IS_CUSTOM = BUNDLE_PREFIX + "COMMAND.IS_CUSTOM";
    public static final String BUNDLE_ROUTER_CANONICAL_READABLE_NAME = BUNDLE_PREFIX + "ROUTER.CANONICAL_READABLE_NAME";
    public static final String BUNDLE_ROUTER_UUID = BUNDLE_PREFIX + "ROUTER.UUID";
    public static final String BUNDLE_ROUTER_VARIABLE_NAME = BUNDLE_PREFIX + "ROUTER.VARIABLE_NAME";
    public static final String BUNDLE_ROUTER_IS_VARIABLE = BUNDLE_PREFIX + "ROUTER.IS_VARIABLE";

    /**
     * Type: {@code int}.
     * <p>
     * versionCode of the plug-in that saved the Bundle.
     */
    /*
     * This extra is not strictly required, however it makes backward and forward compatibility
     * significantly easier. For example, suppose a bug is found in how some version of the plug-in
     * stored its Bundle. By having the version, the plug-in can better detect when such bugs occur.
     */
    @NonNull
    public static final String BUNDLE_EXTRA_INT_VERSION_CODE =
            BUNDLE_PREFIX + "INT_VERSION_CODE"; //$NON-NLS-1$

    /**
     * Method to verify the content of the bundle are correct.
     * <p>
     * This method will not mutate {@code bundle}.
     *
     * @param bundle bundle to verify. May be null, which will always return false.
     * @return true if the Bundle is valid, false if the bundle is invalid.
     */
    public static boolean isBundleValid(@Nullable final Bundle bundle) {
        if (null == bundle) {
            return false;
        }

        //TODO

//        try {
//            BundleAssertions.assertHasString(bundle, BUNDLE_EXTRA_STRING_MESSAGE, false, false);
//            BundleAssertions.assertHasInt(bundle, BUNDLE_EXTRA_INT_VERSION_CODE);
//            BundleAssertions.assertKeyCount(bundle, 2);
//        } catch (final AssertionError e) {
//            Lumberjack.e("Bundle failed verification%s", e); //$NON-NLS-1$
//            return false;
//        }

        return true;
    }

    /**
     * @param bundle A valid plug-in bundle.
     * @return The message inside the plug-in bundle.
     */
//    @NonNull
//    public static String getMessage(@NonNull final Bundle bundle) {
//        return bundle.getString(BUNDLE_EXTRA_STRING_MESSAGE);
//    }

    /**
     * Private constructor prevents instantiation
     *
     * @throws UnsupportedOperationException because this class cannot be instantiated.
     */
    private PluginBundleValues() {
        throw new UnsupportedOperationException("This class is non-instantiable"); //$NON-NLS-1$
    }

    public static String getBundleBlurb(Bundle bundle) {
        final StringBuilder stringBuilder = new StringBuilder();

        if (bundle.getBoolean(BUNDLE_ROUTER_IS_VARIABLE, false)) {
            stringBuilder.append("- Router Variable Name : ")
                    .append(bundle.getString(BUNDLE_ROUTER_VARIABLE_NAME));
        } else {
            stringBuilder.append("- Router : ")
                    .append(bundle.getString(BUNDLE_ROUTER_CANONICAL_READABLE_NAME));
        }
        stringBuilder.append("\n\n");
        if (bundle.getBoolean(BUNDLE_COMMAND_CUSTOM_IS_VARIABLE, false)) {
            stringBuilder.append("- Command Variable Name : ")
                    .append(bundle.getString(BUNDLE_COMMAND_CUSTOM_VARIABLE_NAME));
        } else {
            stringBuilder.append("- Command : ");
            if (bundle.getBoolean(BUNDLE_COMMAND_IS_CUSTOM, false)) {
                stringBuilder.append("Custom");
            } else {
                stringBuilder.append(bundle.getString(BUNDLE_COMMAND_SUPPORTED_READABLE_NAME,
                        "-"));
                final CharSequence paramHint = bundle.getString(BUNDLE_COMMAND_SUPPORTED_PARAM_HINT);
                if (!TextUtils.isEmpty(paramHint)) {
                    //Get param value
                    stringBuilder.append("\n").append(" - ")
                            .append(paramHint)
                            .append(" : ")
                            .append(bundle
                                    .getString(BUNDLE_COMMAND_SUPPORTED_PARAM, "-"));
                }
            }
        }

        if (bundle.getBoolean(BUNDLE_OUTPUT_IS_VARIABLE, false)) {
            stringBuilder.append("\n\n");
            stringBuilder.append("- Output Variable Name: ")
                    .append(bundle.getString(BUNDLE_OUTPUT_VARIABLE_NAME));
        }

        return stringBuilder.toString();
    }

    public static Bundle generateBundle(Context context,
                                        boolean isVariableRouter,
                                        Editable selectedRouterVariableName,
                                        CharSequence selectedRouterUuid,
                                        String selectedRouterReadableName,

                                        boolean isCustomCommand,
                                        boolean isVariableCustomCommand,
                                        Editable customCommandConfiguration,
                                        EditActivity.SupportedCommand supportedCommand,
                                        Editable supportedCommandParam,

                                        boolean isVariableReturnOuput,
                                        Editable returnOutputVariableName) {

        assertNotNull(context, "context"); //$NON-NLS-1$

        final Bundle result = new Bundle();
        result.putInt(BUNDLE_EXTRA_INT_VERSION_CODE, AppBuildInfo.getVersionCode(context));

        result.putBoolean(BUNDLE_ROUTER_IS_VARIABLE, isVariableRouter);
        if (selectedRouterVariableName != null)
            result.putString(BUNDLE_ROUTER_VARIABLE_NAME, selectedRouterVariableName.toString());
        if (selectedRouterUuid != null)
            result.putString(BUNDLE_ROUTER_UUID, selectedRouterUuid.toString());
        result.putString(BUNDLE_ROUTER_CANONICAL_READABLE_NAME,
                selectedRouterReadableName);

        result.putBoolean(BUNDLE_COMMAND_IS_CUSTOM, isCustomCommand);
        result.putBoolean(BUNDLE_COMMAND_CUSTOM_IS_VARIABLE, isVariableCustomCommand);
        if (customCommandConfiguration != null) {
            if (isVariableCustomCommand) {
                result.putString(BUNDLE_COMMAND_CUSTOM_VARIABLE_NAME,
                        customCommandConfiguration.toString());
            } else {
                result.putString(BUNDLE_COMMAND_CUSTOM_CMD,
                        customCommandConfiguration.toString());
            }
        }
        if (supportedCommand != null) {
            result.putString(BUNDLE_COMMAND_SUPPORTED_NAME,
                    supportedCommand.name());
            result.putString(BUNDLE_COMMAND_SUPPORTED_READABLE_NAME,
                    supportedCommand.humanReadableName);
            if (!TextUtils.isEmpty(supportedCommand.paramHumanReadableHint)) {
                result.putString(BUNDLE_COMMAND_SUPPORTED_PARAM_HINT,
                        supportedCommand.paramHumanReadableHint);
            }
        }
        if (supportedCommandParam != null)
            result.putString(BUNDLE_COMMAND_SUPPORTED_PARAM, supportedCommandParam.toString());

        result.putBoolean(BUNDLE_OUTPUT_IS_VARIABLE, isVariableReturnOuput);
        if (returnOutputVariableName != null)
            result.putString(BUNDLE_OUTPUT_VARIABLE_NAME, returnOutputVariableName.toString());

        Crashlytics.log(Log.DEBUG, Constants.TAG, "result: " + result);

        return result;

    }
}
