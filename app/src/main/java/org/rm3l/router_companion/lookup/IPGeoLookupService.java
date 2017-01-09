package org.rm3l.router_companion.lookup;

import org.rm3l.ddwrt.BuildConfig;
import org.rm3l.router_companion.resources.IPWhoisInfo;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.Path;

/**
 * Created by rm3l on 27/06/16.
 */
public interface IPGeoLookupService {

    @Headers({
            "Content-Type: application/json",
            "User-Agent: " + BuildConfig.APPLICATION_ID + " v" + BuildConfig.VERSION_NAME
    })
    @GET("{ipOrHost}.json")
    Call<IPWhoisInfo> lookupIP(@Path("ipOrHost") final String ipOrHost);
}
