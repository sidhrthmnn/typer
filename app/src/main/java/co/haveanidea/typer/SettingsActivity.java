package co.haveanidea.typer;

import android.Manifest;
import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.graphics.Color;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.net.URI;
import java.util.*;

public class SettingsActivity extends Activity {
    private LinearLayout page;
    private android.content.SharedPreferences prefs;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); prefs=Prefs.get(this);
        ScrollView scroll=new ScrollView(this); page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(28,32,28,40);
        page.setBackgroundColor(Color.rgb(245,246,239)); scroll.addView(page); setContentView(scroll);
        page.setOnApplyWindowInsetsListener((v,insets)-> { v.setPadding(28,insets.getSystemWindowInsetTop()+20,28,insets.getSystemWindowInsetBottom()+30); return insets; });
        title("Typer",32); title("A little more thought. A lot less typing.",16);
        button("1 · Enable Typer",()->startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        button("2 · Choose Typer",()->((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker());
        title("Make it yours",22);
        toggle("Number row","numbers",true); toggle("Autocorrect common English typos","correct",true);
        toggle("Double-space for a full stop","period",true); toggle("Haptic feedback","haptic",true);
        toggle("Experimental English swipe typing","glide",false); toggle("Incognito: disable learning, clipboard and voice","incognito",false);
        choose("Theme","theme",new String[]{"System","Light","Dark"},0);
        choose("Key height","height",new String[]{"Compact","Comfortable","Large"},1);
        choose("One-handed layout","hand",new String[]{"Full width","Left","Right"},0);
        title("Personal dictionary",22);
        EditText word=field("Add a word",false); button("Add word",()->{
            String value=word.getText().toString().trim().toLowerCase(Locale.ROOT);
            if(!value.matches("[a-z']{2,40}")) { toast("Enter an English word, 2–40 characters."); return; }
            Set<String> words=new HashSet<>(prefs.getStringSet("dictionary",Collections.emptySet()));
            if(words.size()>=500) { toast("Dictionary full. Clear it to add more."); return; }
            words.add(value);prefs.edit().putStringSet("dictionary",words).apply();word.setText("");toast("Added");
        });
        button("Clear dictionary and saved clipboard",()->new AlertDialog.Builder(this).setMessage("Delete your saved words and clips?").setNegativeButton("Cancel",null)
            .setPositiveButton("Delete",(d,w)->prefs.edit().remove("dictionary").remove("clips").apply()).show());
        title("Voice & Rambler-style editing",22);
        title("Basic cleanup works locally after transcription. AI cleanup and spoken rewriting need your own server. Review every result before inserting it.",15);
        button("Allow microphone",()->requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},10));
        toggle("Allow system speech service if on-device speech is unavailable","networkSpeech",false);
        title("System speech may send audio to your device’s speech provider. This fallback is off by default.",14);
        EditText endpoint=field("HTTPS server URL, ending in /rewrite",false);endpoint.setText(prefs.getString("endpoint",""));
        EditText token=field("Server access token (blank keeps current)",true);
        button("Save AI connection",()->{
            String address=endpoint.getText().toString().trim();
            try {
                if(!address.isEmpty()) { URI uri=new URI(address); if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null) throw new Exception(); }
                if(token.length()>0) Prefs.saveToken(this,token.getText().toString());
                prefs.edit().putString("endpoint",address).apply(); token.setText("");toast("Saved");
            } catch(Exception ex) { toast("Check the HTTPS URL or device credential storage."); }
        });
        button("Remove AI connection",()->{
            try { Prefs.saveToken(this,""); prefs.edit().remove("endpoint").apply();endpoint.setText("");toast("Removed"); } catch(Exception ex) { toast("Could not clear connection."); }
        });
        title("Only a tap on ‘AI polish’ or ‘Voice edit’ sends the current voice draft to your configured server. Typed messages and passwords are never sent by Typer. No analytics or ads.",14);
        title("Try your keyboard",22); EditText sample=field("Tap here to type…",false);sample.setMinLines(3);sample.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
    }
    private void title(String text,int size) { TextView view=new TextView(this);view.setText(text);view.setTextSize(size);view.setTextColor(Color.rgb(30,50,42));view.setPadding(0,18,0,14);page.addView(view); }
    private void button(String text,Runnable fn) { Button b=new Button(this);b.setText(text);b.setAllCaps(false);b.setOnClickListener(v->fn.run());page.addView(b); }
    private void toggle(String label,String key,boolean fallback) { Switch s=new Switch(this);s.setText(label);s.setPadding(0,14,0,14);s.setChecked(prefs.getBoolean(key,fallback));s.setOnCheckedChangeListener((b,on)->prefs.edit().putBoolean(key,on).apply());page.addView(s); }
    private void choose(String label,String key,String[] values,int fallback) { title(label,15);Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values));s.setSelection(prefs.getInt(key,fallback));s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){ public void onNothingSelected(AdapterView<?> a){} public void onItemSelected(AdapterView<?> a,View v,int pos,long id){prefs.edit().putInt(key,pos).apply();}});page.addView(s); }
    private EditText field(String hint,boolean password) { EditText e=new EditText(this);e.setHint(hint);e.setSingleLine(true);e.setInputType(password?InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_CLASS_TEXT);page.addView(e);return e; }
    private void toast(String text) { Toast.makeText(this,text,Toast.LENGTH_SHORT).show(); }
}
