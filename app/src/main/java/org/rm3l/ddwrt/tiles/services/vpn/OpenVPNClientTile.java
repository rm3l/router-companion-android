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
package org.rm3l.ddwrt.tiles.services.vpn;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragment;
import com.cocosw.undobar.UndoBarController;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Maps;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.rm3l.ddwrt.DDWRTMainActivity;
import org.rm3l.ddwrt.R;
import org.rm3l.ddwrt.actions.RouterAction;
import org.rm3l.ddwrt.actions.RouterActionListener;
import org.rm3l.ddwrt.actions.SetNVRAMVariablesAction;
import org.rm3l.ddwrt.exceptions.DDWRTNoDataException;
import org.rm3l.ddwrt.exceptions.DDWRTTileAutoRefreshNotAllowedException;
import org.rm3l.ddwrt.mgmt.RouterManagementActivity;
import org.rm3l.ddwrt.resources.conn.NVRAMInfo;
import org.rm3l.ddwrt.resources.conn.Router;
import org.rm3l.ddwrt.tiles.DDWRTTile;
import org.rm3l.ddwrt.tiles.status.wireless.WirelessIfaceTile;
import org.rm3l.ddwrt.utils.SSHUtils;
import org.rm3l.ddwrt.utils.Utils;

import java.util.ArrayList;
import java.util.Map;

import de.keyboardsurfer.android.widget.crouton.Style;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.OPENVPNCL_ADV;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.OPENVPNCL_AUTH;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.OPENVPNCL_BRIDGE;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.OPENVPNCL_CA;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.OPENVPNCL_CERTTYPE;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.OPENVPNCL_CIPHER;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.OPENVPNCL_CLIENT;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.OPENVPNCL_CONFIG;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.OPENVPNCL_ENABLE;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.OPENVPNCL_FRAGMENT;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.OPENVPNCL_IP;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.OPENVPNCL_KEY;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.OPENVPNCL_LZO;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.OPENVPNCL_MASK;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.OPENVPNCL_MSSFIX;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.OPENVPNCL_MTU;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.OPENVPNCL_NAT;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.OPENVPNCL_PKCS_12;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.OPENVPNCL_PROTO;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.OPENVPNCL_REMOTEIP;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.OPENVPNCL_REMOTEPORT;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.OPENVPNCL_ROUTE;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.OPENVPNCL_SEC;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.OPENVPNCL_STATIC;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.OPENVPNCL_TLSAUTH;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.OPENVPNCL_TLSCIP;
import static org.rm3l.ddwrt.resources.conn.NVRAMInfo.OPENVPNCL_TUNTAP;
import static org.rm3l.ddwrt.tiles.status.wireless.WirelessIfaceTile.CAT_SYS_CLASS_NET_S_STATISTICS;
import static org.rm3l.ddwrt.tiles.status.wireless.WirelessIfaceTile.IfaceStatsType.RX_BYTES;
import static org.rm3l.ddwrt.tiles.status.wireless.WirelessIfaceTile.IfaceStatsType.TX_BYTES;
import static org.rm3l.ddwrt.utils.Utils.isThemeLight;

public class OpenVPNClientTile extends DDWRTTile<NVRAMInfo>
        implements DDWRTTile.ActivityResultListener, UndoBarController.AdvancedUndoListener, RouterActionListener  {

    private static final String LOG_TAG = OpenVPNClientTile.class.getSimpleName();
    public static final String OPENVPNCL__DEV = "___openvpncl__dev";
    public static final String OPENVPNCL__DEV_RX_RATE = OPENVPNCL__DEV + "_rx_rate_human_readable";
    public static final String OPENVPNCL__DEV_TX_RATE = OPENVPNCL__DEV + "_tx_rate_human_readable";
    public static final String OPENVPNCL__DEV_RX_PACKETS = OPENVPNCL__DEV + "_rx_packets";
    public static final String OPENVPNCL__DEV_TX_PACKETS = OPENVPNCL__DEV + "_tx_packets";
    public static final String N_A = "N/A";
    public static final String OPENVPNCL_NVRAMINFO = "OPENVPNCL_NVRAMINFO";

    private NVRAMInfo mNvramInfo;

    public OpenVPNClientTile(@NotNull SherlockFragment parentFragment, @NotNull Bundle arguments, @Nullable Router router) {
        super(parentFragment, arguments, router, R.layout.tile_services_openvpn_client, R.id.tile_services_openvpn_client_togglebutton);
    }

    @Override
    public int getTileTitleViewId() {
        return R.id.tile_services_openvpn_client_title;
    }

    @Nullable
    @Override
    protected Loader<NVRAMInfo> getLoader(int id, Bundle args) {
        return new AsyncTaskLoader<NVRAMInfo>(this.mParentFragmentActivity) {

            @Nullable
            @Override
            public NVRAMInfo loadInBackground() {

                try {
                    Log.d(LOG_TAG, "Init background loader for " + OpenVPNClientTile.class + ": routerInfo=" +
                            mRouter + " / this.mAutoRefreshToggle= " + mAutoRefreshToggle + " / nbRunsLoader=" + nbRunsLoader);

                    if (nbRunsLoader > 0 && !mAutoRefreshToggle) {
                        //Skip run
                        Log.d(LOG_TAG, "Skip loader run");
                        return new NVRAMInfo().setException(new DDWRTTileAutoRefreshNotAllowedException());
                    }
                    nbRunsLoader++;

                    mNvramInfo = null;

                    @NotNull final NVRAMInfo nvramInfo = new NVRAMInfo();

                    NVRAMInfo nvramInfoTmp = null;

                    try {
                        nvramInfoTmp =
                                SSHUtils.getNVRamInfoFromRouter(mRouter,
                                        mGlobalPreferences,
                                        //Status: {1,0}
                                        OPENVPNCL_ENABLE,
                                        //Server IP Name
                                        OPENVPNCL_REMOTEIP,
                                        //Port (from 1 to 65535): default=1194
                                        OPENVPNCL_REMOTEPORT,
                                        //Tunnel Device: {"tun", "tap"}
                                        OPENVPNCL_TUNTAP,
                                        //Tunnel Protocol: { "udp", "tcp-client"}
                                        OPENVPNCL_PROTO,
                                        //Encryption Cipher:
                                        //{aes-512-cbc, aes-256-cbc, aes-192-cbc, aes-128-cbc, bf-cbc, none}
                                        OPENVPNCL_CIPHER,
                                        //Hash Algorithm
                                        //{sha512, sha256, sha1, md5, md4, none}
                                        OPENVPNCL_AUTH,
                                        //TLS Cipher
                                        //{TLS-DHE-RSA-WITH-AES-256-GCM-SHA384,
                                        //TLS-DHE-RSA-WITH-AES-256-CBC-SHA256,
                                        //TLS-DHE-RSA-WITH-AES-128-CBC-SHA,
                                        //TLS-RSA-WITH-AES-256-GCM-SHA384,
                                        //TLS-RSA-WITH-AES-256-CBC-SHA256,
                                        //TLS-RSA-WITH-AES-128-CBC-SHA,
                                        //TLS-RSA-WITH-RC4-128-MD5,
                                        //0}
                                        //Advanced Options: {1,0}
                                        OPENVPNCL_ADV,
                                        OPENVPNCL_TLSCIP,
                                        //LZO Compression: {yes, adaptive, no, off}
                                        OPENVPNCL_LZO,
                                        //NAT: {1, 0}
                                        OPENVPNCL_NAT,
                                        //Firewall Protection (enabled if openvpncl_nat==1): {1,0}
                                        OPENVPNCL_SEC,
                                        //Bridge TAP to br0 (enabled if openvpncl_nat==0)"{1,0}
                                        OPENVPNCL_BRIDGE,
                                        //IP Address
                                        OPENVPNCL_IP,
                                        //Subnet Mask
                                        OPENVPNCL_MASK,
                                        //Tunnel MTU Setting: (from 1 to 65535)
                                        OPENVPNCL_MTU,
                                        //Tunnel UDP Fragment (num max chars=5): if nothing => Disabled
                                        OPENVPNCL_FRAGMENT,
                                        //Tunnel UDP MSS-Fix: {1,0}
                                        OPENVPNCL_MSSFIX,
                                        //nsCertType verification: {1,0}
                                        OPENVPNCL_CERTTYPE,
                                        //TLS Auth Key
                                        OPENVPNCL_TLSAUTH,
                                        //Additional Config
                                        OPENVPNCL_CONFIG,
                                        //Policy based Routing
                                        OPENVPNCL_ROUTE,
                                        //PKCS12 Key
                                        OPENVPNCL_PKCS_12,
                                        //Static Key
                                        OPENVPNCL_STATIC,
                                        //CA Cert
                                        OPENVPNCL_CA,
                                        //Public Client Cert
                                        OPENVPNCL_CLIENT,
                                        //Private Client Key
                                        OPENVPNCL_KEY);
                    } finally {
                        if (nvramInfoTmp != null) {
                            nvramInfo.putAll(nvramInfoTmp);
                        }

                        final String[] devDeviceLine = SSHUtils.getManualProperty(mRouter, mGlobalPreferences,
                                "cat /tmp/openvpncl/openvpn.conf | grep \"dev \"");
                        String openvpnclIface = null;
                        if (devDeviceLine != null && devDeviceLine.length > 0) {
                            openvpnclIface = devDeviceLine[0].replace("dev ","").trim();
                        }

                        nvramInfo.setProperty(OPENVPNCL__DEV, openvpnclIface);

                        if (!Strings.isNullOrEmpty(openvpnclIface)) {
                            //noinspection ConstantConditions
                            final Map<WirelessIfaceTile.IfaceStatsType, Long> ifaceRxAndTxRates = getIfaceRxAndTxRates(openvpnclIface);

                            final Long rxBps = ifaceRxAndTxRates.get(RX_BYTES);
                            final Long txBps = ifaceRxAndTxRates.get(TX_BYTES);
                            if (rxBps != null) {
                                nvramInfo.setProperty(OPENVPNCL__DEV_RX_RATE,
                                        rxBps + " B/s (" + FileUtils.byteCountToDisplaySize(rxBps)
                                                + "/s)");
                            }
                            if (txBps != null) {
                                nvramInfo.setProperty(OPENVPNCL__DEV_TX_RATE,
                                        txBps + " B/s (" + FileUtils.byteCountToDisplaySize(txBps)
                                                + "/s)");
                            }

                            //Packet Info
                            final String sysClassNetStatsFolder = String.format(CAT_SYS_CLASS_NET_S_STATISTICS, openvpnclIface);
                            try {
                                final String[] packetsInfo = SSHUtils
                                        .getManualProperty(mRouter, mGlobalPreferences,
                                                String.format("%s/rx_packets", sysClassNetStatsFolder),
                                                String.format("%s/rx_errors", sysClassNetStatsFolder),
                                                String.format("%s/tx_packets", sysClassNetStatsFolder),
                                                String.format("%s/tx_errors", sysClassNetStatsFolder));

                                if (packetsInfo != null) {
                                    final long rxErrors = Long.parseLong(packetsInfo[1]);
                                    nvramInfo.setProperty(OPENVPNCL__DEV_RX_PACKETS,
                                            String.format("%s (%s)",
                                                    packetsInfo[0], rxErrors <=0 ? "no error" :
                                                            (rxErrors + String.format("error%s", rxErrors > 1 ? "s" : ""))));
                                    final long txErrors = Long.parseLong(packetsInfo[3]);
                                    nvramInfo.setProperty(OPENVPNCL__DEV_TX_PACKETS,
                                            String.format("%s (%s)",
                                                    packetsInfo[0], txErrors <=0 ? "no error" :
                                                            (txErrors + String.format(" error%s", txErrors > 1 ? "s" : ""))));
                                }
                            } catch (final Exception e) {
                                e.printStackTrace();
                                //No worries
                            }
                        }

                    }

                    if (nvramInfo.isEmpty()) {
                        throw new DDWRTNoDataException("No Data!");
                    }

                    return nvramInfo;

                } catch (@NotNull final Exception e) {
                    e.printStackTrace();
                    return new NVRAMInfo().setException(e);
                }

            }

            @NotNull
            private Map<WirelessIfaceTile.IfaceStatsType, Long> getIfaceRxAndTxRates(@NotNull final String phyIface) {
                final Map<WirelessIfaceTile.IfaceStatsType, Long> result = Maps.newHashMapWithExpectedSize(2);
                final String sysClassNetStatsFolder = String.format(CAT_SYS_CLASS_NET_S_STATISTICS, phyIface);
                final String rxBytesCmd = String
                        .format("%s/rx_bytes", sysClassNetStatsFolder);
                final String txBytesCmd = String
                        .format("%s/tx_bytes", sysClassNetStatsFolder);

                try {
                    final long[] bytesBeforeAndAfter = parseFloatDataFromOutput(SSHUtils.getManualProperty(mRouter, mGlobalPreferences,
                                    rxBytesCmd,
                                    txBytesCmd,
                                    "sleep 1",
                                    rxBytesCmd,
                                    txBytesCmd)
                    );
                    if (bytesBeforeAndAfter.length >= 4) {
                        result.put(RX_BYTES,
                                Math.abs(bytesBeforeAndAfter[1] - bytesBeforeAndAfter[0]));
                        result.put(TX_BYTES,
                                Math.abs(bytesBeforeAndAfter[3] - bytesBeforeAndAfter[2]));
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
                return result;
            }

            @NotNull
            private long[] parseFloatDataFromOutput(@Nullable final String[] output) {
                if (output == null || output.length == 0) {
                    throw new IllegalArgumentException("Output null or empty");
                }
                final long[] result = new long[output.length];
                for (int i = 0; i < output.length; i++) {
                    result[i] = Long.parseLong(output[i]);

                }
                return result;
            }
        };
    }

    @Nullable
    @Override
    protected String getLogTag() {
        return LOG_TAG;
    }

    @Override
    public void onLoadFinished(Loader<NVRAMInfo> loader, NVRAMInfo data) {
        Log.d(LOG_TAG, "onLoadFinished: loader=" + loader + " / data=" + data);

        layout.findViewById(R.id.tile_services_openvpn_client_header_loading_view)
                .setVisibility(View.GONE);
        layout.findViewById(R.id.tile_services_openvpn_client_loading_view)
                .setVisibility(View.GONE);
        final View openvpnclStatus = layout.findViewById(R.id.tile_services_openvpn_client_status);
        openvpnclStatus
                .setVisibility(View.VISIBLE);
        layout.findViewById(R.id.tile_services_openvpn_client_grid_layout)
                .setVisibility(View.VISIBLE);
        layout.findViewById(R.id.tile_services_openvpn_client_note)
                .setVisibility(View.VISIBLE);

        if (data == null) {
            data = new NVRAMInfo().setException(new DDWRTNoDataException("No Data!"));
        }

        @NotNull final TextView errorPlaceHolderView = (TextView) this.layout.findViewById(R.id.tile_services_openvpn_client_error);

        @Nullable final Exception exception = data.getException();

        if (!(exception instanceof DDWRTTileAutoRefreshNotAllowedException)) {

            mNvramInfo = new NVRAMInfo();
            mNvramInfo.putAll(data);

            if (exception == null) {
                errorPlaceHolderView.setVisibility(View.GONE);
            }

            //State
            final String statusKey = \"fake-key\";
            final String statusValue;
            switch (statusKey) {
                case "1":
                    statusValue = "Enabled";
                    break;
                case "0":
                    statusValue = "Disabled";
                    break;
                default:
                    statusValue = N_A;
                    break;
            }
            ((TextView) openvpnclStatus).setText(statusValue);

            ((TextView) layout.findViewById(R.id.tile_services_openvpn_client_state)).setText(statusValue);

            //Server IP/Name
            ((TextView) layout.findViewById(R.id.tile_services_openvpn_client_server_ip_name))
                    .setText(data.getProperty(OPENVPNCL_REMOTEIP, N_A));

            //Port
            ((TextView) layout.findViewById(R.id.tile_services_openvpn_client_server_port))
                    .setText(data.getProperty(OPENVPNCL_REMOTEPORT, N_A));

            //BW In
            ((TextView) layout.findViewById(R.id.tile_services_openvpn_client_rx_rate))
                    .setText(data.getProperty(OPENVPNCL__DEV_RX_RATE, N_A));

            //BW Out
            ((TextView) layout.findViewById(R.id.tile_services_openvpn_client_tx_rate))
                    .setText(data.getProperty(OPENVPNCL__DEV_TX_RATE, N_A));

            //RX Packets
            ((TextView) layout.findViewById(R.id.tile_services_openvpn_client_rx_packets))
                    .setText(data.getProperty(OPENVPNCL__DEV_RX_PACKETS, N_A));

            //TX Packets
            ((TextView) layout.findViewById(R.id.tile_services_openvpn_client_tx_packets))
                    .setText(data.getProperty(OPENVPNCL__DEV_TX_PACKETS, N_A));

            //Tunnel Device
            //noinspection ConstantConditions
            ((TextView) layout.findViewById(R.id.tile_services_openvpn_client_tunnel_device))
                    .setText(data.getProperty(OPENVPNCL_TUNTAP, N_A).toUpperCase());

            //Tunnel Proto
            final String protoProp = data.getProperty(OPENVPNCL_PROTO, N_A);
            final String tunnelProto;
            if ("tcp-client".equalsIgnoreCase(protoProp)) {
                tunnelProto = "TCP";
            } else {
                //noinspection ConstantConditions
                tunnelProto = protoProp.toUpperCase();
            }
            ((TextView) layout.findViewById(R.id.tile_services_openvpn_client_tunnel_protocol))
                    .setText(tunnelProto);

            //Encryption Cipher
            final String encCipherProp = data.getProperty(OPENVPNCL_CIPHER, N_A);
            final String encCipher;
            //noinspection ConstantConditions
            switch (encCipherProp.toLowerCase()) {
                case "aes-512-cbc":
                    encCipher = "AES-512 CBC";
                    break;
                case "aes-256-cbc":
                    encCipher = "AES-256 CBC";
                    break;
                case "aes-192-cbc":
                    encCipher = "AES-192 CBC";
                    break;
                case "aes-128-cbc":
                    encCipher = "AES-128 CBC";
                    break;
                case "bf-cbc":
                    encCipher = "Blowfish CBC";
                    break;
                default:
                    encCipher = encCipherProp;
                    break;
            }
            ((TextView) layout.findViewById(R.id.tile_services_openvpn_client_encryption_cipher))
                    .setText(encCipher);

            //Hash Algo
            final String hashAlgoProp = data.getProperty(OPENVPNCL_AUTH, N_A);
            final String hashAlgo;
            if (!"none".equalsIgnoreCase(hashAlgoProp)) {
                //noinspection ConstantConditions
                hashAlgo = hashAlgoProp.toUpperCase();
            } else {
                hashAlgo = "None";
            }
            ((TextView) layout.findViewById(R.id.tile_services_openvpn_client_hash_algorithm))
                    .setText(hashAlgo);
        }

        if (exception != null && !(exception instanceof DDWRTTileAutoRefreshNotAllowedException)) {
            //noinspection ThrowableResultOfMethodCallIgnored
            final Throwable rootCause = Throwables.getRootCause(exception);
            errorPlaceHolderView.setText("Error: " + (rootCause != null ? rootCause.getMessage() : "null"));
            final Context parentContext = this.mParentFragmentActivity;
            errorPlaceHolderView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    //noinspection ThrowableResultOfMethodCallIgnored
                    if (rootCause != null) {
                        Toast.makeText(parentContext,
                                rootCause.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }
            });
            errorPlaceHolderView.setVisibility(View.VISIBLE);
        }

        doneWithLoaderInstance(this, loader,
                R.id.tile_services_openvpn_client_togglebutton_title, R.id.tile_services_openvpn_client_togglebutton_separator);

        Log.d(LOG_TAG, "onLoadFinished(): done loading!");
    }

    @Nullable
    @Override
    protected OnClickIntent getOnclickIntent() {
        if (mNvramInfo == null) {
            //Loading
            Utils.displayMessage(mParentFragmentActivity, "Loading data from router - please wait a few seconds.", Style.ALERT);
            return null;
        }

        if (mNvramInfo.isEmpty()) {
            //No data!
            Utils.displayMessage(mParentFragmentActivity, "No data available - please retry later.", Style.ALERT);
            return null;
        }

        final String mRouterUuid = mRouter.getUuid();
        final Intent editOpenVPNClSettingsIntent =
                new Intent(mParentFragment.getActivity(),
                        isThemeLight(mParentFragmentActivity, mRouterUuid) ?
                        EditOpenVPNClientSettingsActivityLight.class : EditOpenVPNClientSettingsActivity.class);
        editOpenVPNClSettingsIntent.putExtra(OPENVPNCL_NVRAMINFO, mNvramInfo);
        editOpenVPNClSettingsIntent.putExtra(RouterManagementActivity.ROUTER_SELECTED, mRouterUuid);

        return new OnClickIntent("Loading OpenVPN Client Settings...",
                editOpenVPNClSettingsIntent, this);
    }

    @Override
    public void onResultCode(int resultCode, Intent data) {
        switch (resultCode) {
            case Activity.RESULT_CANCELED:
                Utils.displayMessage(mParentFragmentActivity, "Operation cancelled", Style.INFO);
                break;
            case Activity.RESULT_OK:
                final ArrayList<String> varsChanged = data.getStringArrayListExtra(
                        EditOpenVPNClientSettingsActivity.VARIABLES_CHANGED);
                final NVRAMInfo newNvramInfoData = (NVRAMInfo) data.getSerializableExtra(OPENVPNCL_NVRAMINFO);
                if (newNvramInfoData == null || varsChanged == null || varsChanged.isEmpty()) {
                    Utils.displayMessage(mParentFragmentActivity, "No change", Style.INFO);
                    break;
                }

                final Bundle token = new Bundle();
                token.putString(DDWRTMainActivity.ROUTER_ACTION, RouterAction.SET_NVRAM_VARIABLES.toString());
                token.putSerializable(OPENVPNCL_NVRAMINFO, newNvramInfoData);

                new UndoBarController.UndoBar(mParentFragmentActivity)
                        .message("OpenVPN Client Settings will be updated")
                        .listener(this)
                        .token(token)
                        .show();
                break;
            default:
                break;
        }
    }

    @Override
    public void onHide(@android.support.annotation.Nullable Parcelable parcelable) {
        if (parcelable instanceof Bundle) {
            final Bundle token = (Bundle) parcelable;
            final String routerAction = token.getString(DDWRTMainActivity.ROUTER_ACTION);
            Log.d(LOG_TAG, "routerAction: [" + routerAction + "]");
            if (isNullOrEmpty(routerAction)) {
                return;
            }
            try {
                switch (RouterAction.valueOf(routerAction)) {
                    case SET_NVRAM_VARIABLES:
                        new SetNVRAMVariablesAction(
                                (NVRAMInfo) token.getSerializable(OPENVPNCL_NVRAMINFO),
                                this,
                                mGlobalPreferences)
                                .execute(mRouter);
                        break;
                    default:
                        //Ignored
                        break;
                }
            } catch (IllegalArgumentException | NullPointerException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onClear(@NonNull Parcelable[] parcelables) {
        //Nothing to do
    }

    @Override
    public void onRouterActionSuccess(@NotNull RouterAction routerAction, @NotNull Router router) {
        Utils.displayMessage(mParentFragmentActivity,
                "Success",
                Style.CONFIRM);
    }

    @Override
    public void onRouterActionFailure(@NotNull RouterAction routerAction, @NotNull Router router, @Nullable Exception exception) {
        Utils.displayMessage(mParentFragmentActivity,
                String.format("Error: %s", ExceptionUtils.getRootCauseMessage(exception)),
                Style.ALERT);
    }

    @Override
    public void onUndo(@android.support.annotation.Nullable Parcelable parcelable) {
        //Nothing to do
    }
}
