/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.credentials.provider.ui;

import static androidx.credentials.CredentialOption.BUNDLE_KEY_IS_AUTO_SELECT_ALLOWED;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.service.credentials.CredentialEntry;

import androidx.annotation.RequiresApi;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.os.BuildCompat;
import androidx.credentials.R;
import androidx.credentials.TestUtilsKt;
import androidx.credentials.provider.BeginGetCredentialOption;
import androidx.credentials.provider.BeginGetCustomCredentialOption;
import androidx.credentials.provider.BiometricPromptData;
import androidx.credentials.provider.CustomCredentialEntry;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;

import javax.crypto.NullCipher;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 26) // Instant usage
@SmallTest
public class CustomCredentialEntryJavaTest {
    private static final CharSequence TITLE = "title";
    private static final CharSequence SUBTITLE = "subtitle";
    private static final String TYPE = "custom_type";
    private static final CharSequence TYPE_DISPLAY_NAME = "Password";
    private static final String ENTRY_GROUP_ID = "entryGroupId";
    private static final boolean DEFAULT_SINGLE_PROVIDER_ICON_BIT = false;
    private static final boolean SINGLE_PROVIDER_ICON_BIT = true;
    private static final Long LAST_USED_TIME = 10L;
    private static final Icon ICON = Icon.createWithBitmap(Bitmap.createBitmap(
            100, 100, Bitmap.Config.ARGB_8888));
    private static final boolean IS_AUTO_SELECT_ALLOWED = true;
    private final BeginGetCredentialOption mBeginCredentialOption =
            new BeginGetCustomCredentialOption(
                    "id", "custom_type", new Bundle());
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final Intent mIntent = new Intent();
    private final PendingIntent mPendingIntent =
            PendingIntent.getActivity(mContext, 0, mIntent,
                    PendingIntent.FLAG_IMMUTABLE);
    @Test
    public void build_requiredParameters_success() {
        CustomCredentialEntry entry = constructEntryWithRequiredParams();
        assertNotNull(entry);
        assertEntryWithRequiredParams(entry);
    }
    @Test
    public void build_allParameters_success() {
        CustomCredentialEntry entry = constructEntryWithAllParams();
        assertNotNull(entry);
        assertEntryWithAllParams(entry);
    }
    @Test
    public void build_nullTitle_throwsNPE() {
        assertThrows("Expected null title to throw NPE",
                NullPointerException.class,
                () -> new CustomCredentialEntry.Builder(
                        mContext, TYPE, null, mPendingIntent, mBeginCredentialOption
                ));
    }
    @Test
    public void build_nullContext_throwsNPE() {
        assertThrows("Expected null title to throw NPE",
                NullPointerException.class,
                () -> new CustomCredentialEntry.Builder(
                        null, TYPE, TITLE, mPendingIntent, mBeginCredentialOption
                ).build());
    }
    @Test
    public void build_nullPendingIntent_throwsNPE() {
        assertThrows("Expected null pending intent to throw NPE",
                NullPointerException.class,
                () -> new CustomCredentialEntry.Builder(
                        mContext, TYPE, TITLE, null, mBeginCredentialOption
                ).build());
    }
    @Test
    public void build_nullBeginOption_throwsNPE() {
        assertThrows("Expected null option to throw NPE",
                NullPointerException.class,
                () -> new CustomCredentialEntry.Builder(
                        mContext, TYPE, TITLE, mPendingIntent, null
                ).build());
    }
    @Test
    public void build_emptyTitle_throwsIAE() {
        assertThrows("Expected empty title to throw IAE",
                IllegalArgumentException.class,
                () -> new CustomCredentialEntry.Builder(
                        mContext, TYPE, "", mPendingIntent, mBeginCredentialOption
                ).build());
    }
    @Test
    public void build_emptyType_throwsIAE() {
        assertThrows("Expected empty type to throw NPE",
                IllegalArgumentException.class,
                () -> new CustomCredentialEntry.Builder(
                        mContext, "", TITLE, mPendingIntent, mBeginCredentialOption
                ).build());
    }
    @Test
    public void build_nullIcon_defaultIconSet() {
        CustomCredentialEntry entry = constructEntryWithRequiredParams();
        assertThat(TestUtilsKt.equals(entry.getIcon(),
                Icon.createWithResource(mContext, R.drawable.ic_other_sign_in))).isTrue();
    }
    @Test
    public void builder_setPreferredDefaultIconBit_retrieveSetIconBit() {
        boolean expectedPreferredDefaultIconBit = SINGLE_PROVIDER_ICON_BIT;
        CustomCredentialEntry entry = new CustomCredentialEntry.Builder(
                mContext, TYPE, TITLE, mPendingIntent,
                mBeginCredentialOption)
                .setDefaultIconPreferredAsSingleProvider(expectedPreferredDefaultIconBit)
                .build();
        assertThat(entry.isDefaultIconPreferredAsSingleProvider())
                .isEqualTo(expectedPreferredDefaultIconBit);
    }

    @Test
    @SdkSuppress(minSdkVersion = 28)
    public void builder_constructDefault_containsOnlySetPropertiesAndDefaultValues() {
        CustomCredentialEntry entry = constructEntryWithRequiredParams();

        assertEntryWithRequiredParams(entry);
    }

    @Test
    public void builder_setEmptyEntryGroupId_throwIAE() {
        assertThrows("Expected null title to throw IAE",
                IllegalArgumentException.class,
                () -> new CustomCredentialEntry.Builder(
                        mContext, TYPE, TITLE, mPendingIntent,
                        mBeginCredentialOption)
                        .setEntryGroupId("").build()
        );
    }

    @Test
    public void builder_setNonEmptyEntryGroupId_retrieveSetEntryGroupId() {
        CharSequence expectedEntryGroupId = "pikes-peak";

        CustomCredentialEntry entry = new CustomCredentialEntry.Builder(
                mContext, TYPE, TITLE, mPendingIntent,
                mBeginCredentialOption)
                .setEntryGroupId(expectedEntryGroupId).build();

        assertThat(entry.getEntryGroupId()).isEqualTo(expectedEntryGroupId);
    }

    @Test
    @SdkSuppress(minSdkVersion = 28)
    @SuppressWarnings("deprecation")
    public void fromSlice_requiredParams_success() {
        CustomCredentialEntry originalEntry = constructEntryWithRequiredParams();
        android.app.slice.Slice slice = CustomCredentialEntry
                .toSlice(originalEntry);
        CustomCredentialEntry entry = CustomCredentialEntry.fromSlice(
                slice);
        assertNotNull(entry);
        assertEntryWithRequiredParamsFromSlice(entry);
    }
    @Test
    @SdkSuppress(minSdkVersion = 28)
    @SuppressWarnings("deprecation")
    public void fromSlice_allParams_success() {
        CustomCredentialEntry originalEntry = constructEntryWithAllParams();
        android.app.slice.Slice slice = CustomCredentialEntry
                .toSlice(originalEntry);
        CustomCredentialEntry entry = CustomCredentialEntry.fromSlice(slice);
        assertNotNull(entry);
        assertEntryWithAllParamsFromSlice(entry);
    }
    @Test
    @SdkSuppress(minSdkVersion = 34)
    @SuppressWarnings("deprecation")
    public void fromCredentialEntry_allParams_success() {
        CustomCredentialEntry originalEntry = constructEntryWithAllParams();
        android.app.slice.Slice slice = CustomCredentialEntry.toSlice(originalEntry);
        assertNotNull(slice);
        CustomCredentialEntry entry = CustomCredentialEntry.fromCredentialEntry(
                new CredentialEntry("id", slice));
        assertNotNull(entry);
        assertEntryWithAllParamsFromSlice(entry);
    }

    @Test
    @SdkSuppress(minSdkVersion = 28)
    public void isDefaultIcon_noIconSet_returnsTrue() {
        CustomCredentialEntry entry = new CustomCredentialEntry
                .Builder(mContext, TYPE, TITLE, mPendingIntent, mBeginCredentialOption).build();

        assertTrue(entry.hasDefaultIcon());
    }

    @Test
    @SdkSuppress(minSdkVersion = 28)
    @SuppressWarnings("deprecation")
    public void isDefaultIcon_noIconSetFromSlice_returnsTrue() {
        CustomCredentialEntry entry = new CustomCredentialEntry
                .Builder(mContext, TYPE, TITLE, mPendingIntent, mBeginCredentialOption).build();

        android.app.slice.Slice slice = CustomCredentialEntry.toSlice(entry);

        assertNotNull(slice);

        CustomCredentialEntry entryFromSlice = CustomCredentialEntry.fromSlice(slice);

        assertNotNull(entryFromSlice);
        assertTrue(entryFromSlice.hasDefaultIcon());
        assertTrue(entry.hasDefaultIcon());
    }

    @Test
    @SdkSuppress(minSdkVersion = 28)
    @SuppressWarnings("deprecation")
    public void isDefaultIcon_customIconSetFromSlice_returnsTrue() {
        CustomCredentialEntry entry = new CustomCredentialEntry
                .Builder(mContext, TYPE, TITLE, mPendingIntent, mBeginCredentialOption)
                .setIcon(ICON).build();

        android.app.slice.Slice slice = CustomCredentialEntry.toSlice(entry);

        assertNotNull(slice);

        CustomCredentialEntry entryFromSlice = CustomCredentialEntry.fromSlice(slice);

        assertNotNull(entryFromSlice);
        assertFalse(entryFromSlice.hasDefaultIcon());
        assertFalse(entry.hasDefaultIcon());
    }

    @Test
    @SdkSuppress(minSdkVersion = 28)
    public void isDefaultIcon_customIcon_returnsFalse() {
        CustomCredentialEntry entry = new CustomCredentialEntry
                .Builder(mContext, TYPE, TITLE, mPendingIntent, mBeginCredentialOption)
                .setIcon(ICON).build();

        assertFalse(entry.hasDefaultIcon());
    }

    @Test
    public void isAutoSelectAllowedFromOption_optionAllows_returnsTrue() {
        mBeginCredentialOption.getCandidateQueryData().putBoolean(
                BUNDLE_KEY_IS_AUTO_SELECT_ALLOWED, true);
        CustomCredentialEntry entry = new CustomCredentialEntry
                .Builder(mContext, TYPE, TITLE, mPendingIntent, mBeginCredentialOption).build();

        assertTrue(entry.isAutoSelectAllowedFromOption());
    }

    @Test
    public void isAutoSelectAllowedFromOption_optionDisallows_returnsFalse() {
        CustomCredentialEntry entry = new CustomCredentialEntry
                .Builder(mContext, TYPE, TITLE, mPendingIntent, mBeginCredentialOption).build();

        assertFalse(entry.isAutoSelectAllowedFromOption());
    }

    private CustomCredentialEntry constructEntryWithRequiredParams() {
        return new CustomCredentialEntry.Builder(
                mContext,
                TYPE,
                TITLE,
                mPendingIntent,
                mBeginCredentialOption
        ).build();
    }
    private CustomCredentialEntry constructEntryWithAllParams() {
        CustomCredentialEntry.Builder testBuilder = new CustomCredentialEntry.Builder(
                mContext,
                TYPE,
                TITLE,
                mPendingIntent,
                mBeginCredentialOption)
                .setIcon(ICON)
                .setLastUsedTime(Instant.ofEpochMilli(LAST_USED_TIME))
                .setAutoSelectAllowed(IS_AUTO_SELECT_ALLOWED)
                .setTypeDisplayName(TYPE_DISPLAY_NAME)
                .setEntryGroupId(ENTRY_GROUP_ID)
                .setDefaultIconPreferredAsSingleProvider(SINGLE_PROVIDER_ICON_BIT);

        if (BuildCompat.isAtLeastV()) {
            testBuilder.setBiometricPromptData(testBiometricPromptData());
        }
        return testBuilder.build();
    }
    private void assertEntryWithRequiredParams(CustomCredentialEntry entry) {
        assertThat(TITLE.equals(entry.getTitle()));
        assertThat(TYPE.equals(entry.getType()));
        assertThat(mPendingIntent).isEqualTo(entry.getPendingIntent());
        assertThat(entry.getAffiliatedDomain()).isNull();
        assertThat(entry.getEntryGroupId()).isEqualTo(TITLE);
        assertThat(entry.isDefaultIconPreferredAsSingleProvider()).isEqualTo(
                DEFAULT_SINGLE_PROVIDER_ICON_BIT);
        assertThat(entry.getBiometricPromptData()).isNull();
    }
    private void assertEntryWithRequiredParamsFromSlice(CustomCredentialEntry entry) {
        assertThat(TITLE.equals(entry.getTitle()));
        assertThat(TYPE.equals(entry.getType()));
        assertThat(mPendingIntent).isEqualTo(entry.getPendingIntent());
        assertThat(entry.getAffiliatedDomain()).isNull();
        assertThat(entry.getEntryGroupId()).isEqualTo(TITLE);
        assertThat(entry.isDefaultIconPreferredAsSingleProvider()).isEqualTo(
                DEFAULT_SINGLE_PROVIDER_ICON_BIT);
        assertThat(entry.getBiometricPromptData()).isNull();
    }
    private void assertEntryWithAllParams(CustomCredentialEntry entry) {
        assertThat(TITLE.equals(entry.getTitle()));
        assertThat(TYPE.equals(entry.getType()));
        assertThat(SUBTITLE.equals(entry.getSubtitle()));
        assertThat(TYPE_DISPLAY_NAME.equals(entry.getTypeDisplayName()));
        assertThat(ICON).isEqualTo(entry.getIcon());
        assertThat(Instant.ofEpochMilli(LAST_USED_TIME)).isEqualTo(entry.getLastUsedTime());
        assertThat(IS_AUTO_SELECT_ALLOWED).isEqualTo(entry.isAutoSelectAllowed());
        assertThat(mPendingIntent).isEqualTo(entry.getPendingIntent());
        assertThat(mBeginCredentialOption.getType()).isEqualTo(entry.getType());
        assertThat(mBeginCredentialOption).isEqualTo(entry.getBeginGetCredentialOption());
        assertThat(entry.getAffiliatedDomain()).isNull();
        assertThat(entry.getEntryGroupId()).isEqualTo(ENTRY_GROUP_ID);
        assertThat(entry.isDefaultIconPreferredAsSingleProvider()).isEqualTo(
                SINGLE_PROVIDER_ICON_BIT);
        if (BuildCompat.isAtLeastV() && entry.getBiometricPromptData() != null) {
            assertThat(entry.getBiometricPromptData().getAllowedAuthenticators()).isEqualTo(
                    testBiometricPromptData().getAllowedAuthenticators());
        } else {
            assertThat(entry.getBiometricPromptData()).isNull();
        }
    }
    private void assertEntryWithAllParamsFromSlice(CustomCredentialEntry entry) {
        assertThat(TITLE.equals(entry.getTitle()));
        assertThat(TYPE.equals(entry.getType()));
        assertThat(SUBTITLE.equals(entry.getSubtitle()));
        assertThat(TYPE_DISPLAY_NAME.equals(entry.getTypeDisplayName()));
        assertThat(ICON).isEqualTo(entry.getIcon());
        assertThat(Instant.ofEpochMilli(LAST_USED_TIME)).isEqualTo(entry.getLastUsedTime());
        assertThat(IS_AUTO_SELECT_ALLOWED).isEqualTo(entry.isAutoSelectAllowed());
        assertThat(mPendingIntent).isEqualTo(entry.getPendingIntent());
        assertThat(mBeginCredentialOption.getType()).isEqualTo(entry.getType());
        assertThat(entry.getAffiliatedDomain()).isNull();
        assertThat(entry.getEntryGroupId()).isEqualTo(ENTRY_GROUP_ID);
        assertThat(entry.isDefaultIconPreferredAsSingleProvider()).isEqualTo(
                SINGLE_PROVIDER_ICON_BIT);
        if (BuildCompat.isAtLeastV() && entry.getBiometricPromptData() != null) {
            assertThat(entry.getBiometricPromptData().getAllowedAuthenticators()).isEqualTo(
                    testBiometricPromptData().getAllowedAuthenticators());
        } else {
            assertThat(entry.getBiometricPromptData()).isNull();
        }
    }

    @RequiresApi(35)
    private static BiometricPromptData testBiometricPromptData() {
        return new BiometricPromptData.Builder()
            .setCryptoObject(new BiometricPrompt.CryptoObject(new NullCipher()))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build();
    }
}
