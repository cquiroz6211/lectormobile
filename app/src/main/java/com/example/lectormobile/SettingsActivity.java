package com.example.lectormobile;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

/**
 * Settings screen. Uses the custom UI defined in activity_settings.xml.
 */
public class SettingsActivity extends AppCompatActivity {

    private Spinner themeSpinner;
    private Spinner fontSizeSpinner;
    private TextView versionValue;
    private SharedPreferences prefs;

    private static final String[] THEME_VALUES = {
            ReadingTheme.THEME_LIGHT,
            ReadingTheme.THEME_SEPIA,
            ReadingTheme.THEME_DARK
    };

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

        // Theme spinner
        themeSpinner = findViewById(R.id.settings_spinner_theme);
        ArrayAdapter<CharSequence> themeAdapter = ArrayAdapter.createFromResource(
                this, R.array.theme_entries, android.R.layout.simple_spinner_dropdown_item);
        themeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        themeSpinner.setAdapter(themeAdapter);

        String currentTheme = ReadingTheme.getTheme(prefs);
        int themePos = 2; // Dark default
        if (ReadingTheme.THEME_LIGHT.equals(currentTheme)) {
            themePos = 0;
        } else if (ReadingTheme.THEME_SEPIA.equals(currentTheme)) {
            themePos = 1;
        }
        themeSpinner.setSelection(themePos);

        themeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedTheme = THEME_VALUES[position];
                String prevTheme = ReadingTheme.getTheme(prefs);
                if (!selectedTheme.equals(prevTheme)) {
                    ReadingTheme.setTheme(prefs, selectedTheme);
                    String toastMsg;
                    if (position == 0) {
                        toastMsg = "Tema claro activado";
                    } else if (position == 1) {
                        toastMsg = "Tema sepia activado";
                    } else {
                        toastMsg = "Tema oscuro activado";
                    }
                    Toast.makeText(SettingsActivity.this, toastMsg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
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