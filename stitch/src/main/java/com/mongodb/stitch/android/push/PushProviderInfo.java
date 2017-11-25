package com.mongodb.stitch.android.push;

import android.content.Context;
import android.content.SharedPreferences;

import com.mongodb.stitch.android.push.gcm.GCMPushProviderInfo;

import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.mongodb.stitch.android.push.PushClient.PREF_CONFIGS;
import static com.mongodb.stitch.android.push.PushClient.SHARED_PREFERENCES_NAME;

public abstract class PushProviderInfo {

    private final PushProviderName _providerName;
    private final String _serviceName;

    protected PushProviderInfo(final PushProviderName providerName, final String serviceName) {
        _providerName = providerName;
        _serviceName = serviceName;
    }

    /**
     * @return The name of this push provider.
     */
    public final PushProviderName getProvider() {
        return _providerName;
    }

    /**
     * @return The service handling this type of provider.
     */
    public final String getService() {
        return _serviceName;
    }

    /**
     * @return The provider info as a serializable document.
     */
    public Document toDocument() {
        final Document doc = new Document();
        doc.put(Fields.TYPE, _providerName.getTypeName());
        doc.put(Fields.CONFIG, new Document());
        return doc;
    }

    /**
     * @param context The Context that has access to the app's preferences.
     * @param clientAppId The app ID that the preferences are for.
     * @return All persisted push provider information.
     */
    synchronized static List<PushProviderInfo> fromPreferences(final Context context, final String clientAppId) {
        final String globPrefPath = String.format(SHARED_PREFERENCES_NAME, clientAppId);
        final SharedPreferences preferences = context.getSharedPreferences(globPrefPath, Context.MODE_PRIVATE);
        final Document configs = Document.parse(preferences.getString(PREF_CONFIGS, "{}"));

        final List<PushProviderInfo> providers = new ArrayList<>();
        for (final Map.Entry<String, Object> configEntry : configs.entrySet()) {
            final Document info = (Document) configEntry.getValue();

            final PushProviderName providerName =
                    PushProviderName.fromTypeName(info.getString(PushProviderInfo.Fields.TYPE));
            final Document config = (Document) info.get(PushProviderInfo.Fields.CONFIG);

            final PushProviderInfo provider;
            switch (providerName) {
                case GCM:
                    provider = GCMPushProviderInfo.fromConfig(configEntry.getKey(), config);
                    break;
                default:
                    throw new IllegalStateException("Unknown provider name");
            }
            providers.add(provider);
        }
        return providers;
    }

    public class Fields {
        public static final String NAME = "name";
        public static final String TYPE = "type";
        public static final String CONFIG = "config";
    }
}
