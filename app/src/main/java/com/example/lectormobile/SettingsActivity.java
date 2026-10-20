package com.example.lectormobile;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;

/**
 * Settings screen. Uses the custom UI defined in activity_settings.xml.
 */
public class SettingsActivity extends AppCompatActivity {

    private SwitchMaterial darkModeSwitch;
    private Spinner fontSizeSpinner;
    private TextView versionValue;
    private SharedPreferences prefs;

    // Font size values (px)
    private static final int[] FONT_SIZES = {12, 14, 16, 18, 20, 22, 24, 26, 28};
    private static final String[] FONT_LABELS = {"Muy pequeña", "Pequeña", "S", "M", "L", "XL", "XXL", "XXXL", "XXXXL"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Toolbar toolbar = findViewById(R.id.settings_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Dark mode switch
        darkModeSwitch = findViewById(R.id.settings_switch_dark_mode);
        darkModeSwitch.setChecked(prefs.getBoolean("dark_mode", true));
        darkModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("dark_mode", isChecked).apply();
            Toast.makeText(this,
                    isChecked ? "Fondo oscuro activado" : "Fondo claro activado",
                    Toast.LENGTH_SHORT).show();
        });

        // Font size spinner
        fontSizeSpinner = findViewById(R.id.settings_spinner_font_size);
        ArrayAdapter<CharSequence> fontAdapter = ArrayAdapter.createFromResource(
                this, R.array.font_sizes, android.R.layout.simple_spinner_dropdown_item);
        fontAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        fontSizeSpinner.setAdapter(fontAdapter);

        int currentFont = prefs.getInt("font_size", 18);
        int defaultPos = 3; // Default index for 18px
        for (int i = 0; i < FONT_SIZES.length; i++) {
            if (FONT_SIZES[i] == currentFont) {
                defaultPos = i;
                break;
            }
        }
        fontSizeSpinner.setSelection(defaultPos);

        fontSizeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int size = FONT_SIZES[position];
                prefs.edit().putInt("font_size", size).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // Version number (from the installed package)
        versionValue = findViewById(R.id.settings_version_value);
        try {
            versionValue.setText(getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName);
        } catch (Exception e) {
            versionValue.setText("");
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}