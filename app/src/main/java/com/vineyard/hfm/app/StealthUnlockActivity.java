package com.vineyard.hfm.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Gate activity to manage Hide & Stealth slider icons visibility.
 * Supports 3 security verification methods:
 * 1. 4-digit PIN / Biometric (Fingerprint)
 * 2. 3-step Physical Sensor Ritual (Taps -> Shakes -> Compass Orientation)
 * 3. Map Coordinate Fallback (Pin-drop geographic verification)
 */
public class StealthUnlockActivity extends FragmentActivity {

    public static final int REQUEST_CODE_RITUAL = 881;
    public static final int REQUEST_CODE_MAP = 882;
    public static boolean sStealthUnlocked = false;

    private TextView tvDescription;
    private Button btnToggle;
    private Button btnRitual;
    private Button btnMap;
    private Button btnCancel;

    private BiometricPrompt biometricPrompt;
    private BiometricPrompt.PromptInfo promptInfo;
    private boolean isCurrentlyHidden;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stealth_unlock);

        sStealthUnlocked = false;

        tvDescription = findViewById(R.id.tv_stealth_description);
        btnToggle = findViewById(R.id.btn_stealth_toggle);
        btnRitual = findViewById(R.id.btn_stealth_ritual);
        btnMap = findViewById(R.id.btn_stealth_map);
        btnCancel = findViewById(R.id.btn_stealth_cancel);

        SharedPreferences prefs = getSharedPreferences("hfm_stealth_prefs", Context.MODE_PRIVATE);
        isCurrentlyHidden = prefs.getBoolean("is_stealth_hidden", false);

        updateUI();
        setupBiometrics();

        // 1. PIN / Biometric Option
        btnToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BiometricManager biometricManager = BiometricManager.from(StealthUnlockActivity.this);
                int canAuthenticate = biometricManager.canAuthenticate(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG | BiometricManager.Authenticators.BIOMETRIC_WEAK
                );

                if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
                    biometricPrompt.authenticate(promptInfo);
                } else {
                    // Fallback directly to PIN
                    showPinFallbackDialog();
                }
            }
        });

        // 2. Physical Sensor Ritual Option
        if (btnRitual != null) {
            btnRitual.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    RitualManager ritualManager = new RitualManager();
                    List<RitualManager.Ritual> rituals = ritualManager.loadRituals(StealthUnlockActivity.this);

                    if (rituals != null && !rituals.isEmpty()) {
                        Intent intent = new Intent(StealthUnlockActivity.this, RitualVerifyTapsActivity.class);
                        intent.putExtra("ritual_index", 0);
                        intent.putExtra("is_stealth_unlock", true);
                        startActivityForResult(intent, REQUEST_CODE_RITUAL);
                    } else {
                        Toast.makeText(StealthUnlockActivity.this, "No saved Ritual found. Please use PIN or create a Ritual first.", Toast.LENGTH_LONG).show();
                    }
                }
            });
        }

        // 3. Map Coordinate Fallback Option
        if (btnMap != null) {
            btnMap.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(StealthUnlockActivity.this, MapFallbackUnlockActivity.class);
                    intent.putExtra("is_stealth_unlock", true);
                    startActivityForResult(intent, REQUEST_CODE_MAP);
                }
            });
        }

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sStealthUnlocked) {
            sStealthUnlocked = false;
            executeToggle();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if ((requestCode == REQUEST_CODE_RITUAL || requestCode == REQUEST_CODE_MAP) && resultCode == RESULT_OK) {
            executeToggle();
        }
    }

    private void updateUI() {
        if (isCurrentlyHidden) {
            tvDescription.setText("Identity Verification Required.\nChoose your verification method to RESTORE (UNHIDE) the HFM Hide & Stealth slider options:");
            btnToggle.setText("1. UNHIDE VIA PIN / FINGERPRINT");
            if (btnRitual != null) btnRitual.setText("2. UNHIDE VIA SENSOR RITUAL");
            if (btnMap != null) btnMap.setText("3. UNHIDE VIA MAP FALLBACK");
        } else {
            tvDescription.setText("Identity Verification Required.\nChoose your verification method to HIDE the HFM Hide & Stealth slider options:");
            btnToggle.setText("1. HIDE VIA PIN / FINGERPRINT");
            if (btnRitual != null) btnRitual.setText("2. HIDE VIA SENSOR RITUAL");
            if (btnMap != null) btnMap.setText("3. HIDE VIA MAP FALLBACK");
        }
    }

    private void setupBiometrics() {
        Executor executor = ContextCompat.getMainExecutor(this);
        biometricPrompt = new BiometricPrompt(this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                executeToggle();
            }

            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                showPinFallbackDialog();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(StealthUnlockActivity.this, "Fingerprint not recognized. Try again or tap 'Use PIN'.", Toast.LENGTH_SHORT).show();
            }
        });

        promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("HFM Stealth Gate")
                .setSubtitle("Confirm fingerprint or enter PIN to change slider option visibility")
                .setNegativeButtonText("Use PIN")
                .build();
    }

    /**
     * Fallback authentication method prompting for the 4-digit PIN.
     */
    private void showPinFallbackDialog() {
        if (isFinishing() || isDestroyed()) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_send_drop, null);
        final AutoCompleteTextView pinInput = dialogView.findViewById(R.id.edit_text_receiver_username);
        pinInput.setHint("Enter 4-digit Stealth PIN");

        builder.setTitle("Enter Stealth Manager PIN")
                .setView(dialogView)
                .setPositiveButton("Verify", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String enteredPin = pinInput.getText().toString().trim();
                        SharedPreferences prefs = getSharedPreferences("hfm_stealth_prefs", Context.MODE_PRIVATE);
                        String savedPin = prefs.getString("stealth_pin", "");

                        if (enteredPin.equals(savedPin) && !savedPin.isEmpty()) {
                            Toast.makeText(StealthUnlockActivity.this, "PIN Verified Successfully!", Toast.LENGTH_SHORT).show();
                            executeToggle();
                        } else {
                            Toast.makeText(StealthUnlockActivity.this, "Incorrect PIN. Access Denied.", Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

        builder.create().show();
    }

    private void executeToggle() {
        SharedPreferences prefs = getSharedPreferences("hfm_stealth_prefs", Context.MODE_PRIVATE);

        if (isCurrentlyHidden) {
            // UNHIDE SLIDER OPTIONS
            prefs.edit().putBoolean("is_stealth_hidden", false).apply();
            Toast.makeText(this, "HFM Slider Options RESTORED.", Toast.LENGTH_LONG).show();
        } else {
            // HIDE SLIDER OPTIONS
            prefs.edit().putBoolean("is_stealth_hidden", true).apply();
            Toast.makeText(this, "HFM Slider Options HIDDEN.", Toast.LENGTH_LONG).show();
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);

        finish();
    }
}