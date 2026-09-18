package co.haveanidea.typer;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.icu.text.BreakIterator;
import android.inputmethodservice.InputMethodService;
import android.os.*;
import android.speech.*;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONArray;

public class TyperService extends InputMethodService {
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private android.content.SharedPreferences prefs;
    private TypingEngine engine;
    private final List<String> baseWords=new ArrayList<>();
    private LinearLayout root,body,candidates;
    private final Map<Button,String> letterButtons=new HashMap<>();
    private final StringBuilder composing=new StringBuilder();
    private String page="letters",language="en",draft="",raw="",previousDraft="",voiceStatus="";
    private String undoOriginal="",undoCommitted="";
    private boolean shift,caps,privateMode,allowWords,numeric,listening,busy;
    private int session,voiceRequest,selectionStart,selectionEnd,bg,fg,keyBg,accent;
    private long lastShift,lastSpace;
    private SpeechRecognizer speech;
    private PopupWindow popup;

    @Override public void onCreate() {
        super.onCreate();prefs=Prefs.get(this);
        List<String> words=new ArrayList<>();
        try(BufferedReader r=new BufferedReader(new InputStreamReader(getAssets().open("words.txt")))) {String s;while((s=r.readLine())!=null)if(!s.trim().isEmpty())words.add(s.trim());}catch(IOException ignored){}
        baseWords.addAll(words);engine=new TypingEngine(baseWords);
    }
    @Override public View onCreateInputView() { root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);render();return root; }
    @Override public boolean onEvaluateFullscreenMode(){return false;}
    @Override public void onStartInput(EditorInfo info,boolean restarting) {
        super.onStartInput(info,restarting);resetVoice();composing.setLength(0);undoCommitted="";undoOriginal="";lastSpace=0;caps=false;
        selectionStart=info.initialSelStart;selectionEnd=info.initialSelEnd;
        int cls=info.inputType&InputType.TYPE_MASK_CLASS, variation=info.inputType&InputType.TYPE_MASK_VARIATION;
        boolean password=(cls==InputType.TYPE_CLASS_TEXT&&(variation==InputType.TYPE_TEXT_VARIATION_PASSWORD||variation==InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD||variation==InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD))
            ||(cls==InputType.TYPE_CLASS_NUMBER&&variation==InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        privateMode=password||prefs.getBoolean("incognito",false)||(info.imeOptions&EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)!=0;
        numeric=cls==InputType.TYPE_CLASS_NUMBER||cls==InputType.TYPE_CLASS_PHONE||cls==InputType.TYPE_CLASS_DATETIME;
        allowWords=!privateMode&&cls==InputType.TYPE_CLASS_TEXT&&variation!=InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS&&variation!=InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS&&variation!=InputType.TYPE_TEXT_VARIATION_URI
            &&(info.inputType&(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS|InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE))==0;
        page=numeric?"numbers":"letters";
        engine=new TypingEngine(baseWords);for(String w:prefs.getStringSet("dictionary",Collections.emptySet()))engine.add(w);
        updateShift();if(root!=null)render();
    }
    @Override public void onStartInputView(EditorInfo info,boolean restarting){super.onStartInputView(info,restarting);onStartInput(info,restarting);render();}
    @Override public void onFinishInputView(boolean finishingInput){resetVoice();finishWord(false,"");dismissPopup();super.onFinishInputView(finishingInput);}
    @Override public void onFinishInput(){resetVoice();composing.setLength(0);super.onFinishInput();}
    @Override public void onDestroy(){resetVoice();worker.shutdownNow();super.onDestroy();}
    @Override public void onCurrentInputMethodSubtypeChanged(InputMethodSubtype subtype){language=subtype.getLocale().startsWith("ml")?"ml":"en";finishWord(false,"");if(root!=null)render();}
    @Override public void onUpdateSelection(int oldStart,int oldEnd,int newStart,int newEnd,int candidateStart,int candidateEnd){
        super.onUpdateSelection(oldStart,oldEnd,newStart,newEnd,candidateStart,candidateEnd);
        selectionStart=newStart;selectionEnd=newEnd;
        if(composing.length()>0&&(newStart!=candidateEnd||newEnd!=candidateEnd)) {composing.setLength(0);if(getCurrentInputConnection()!=null)getCurrentInputConnection().finishComposingText();suggest();}
        if((listening||busy||!draft.isEmpty())&&(oldStart!=newStart||oldEnd!=newEnd)) {resetVoice();if(root!=null)render();}
        updateShift();
    }
    private void resetVoice(){session++;voiceRequest++;listening=false;busy=false;draft="";raw="";previousDraft="";voiceStatus="";if(speech!=null){speech.cancel();speech.destroy();speech=null;}main.removeCallbacksAndMessages(null);}
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private boolean dark(){int choice=prefs.getInt("theme",0);return choice==2||(choice==0&&(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES);}
    private GradientDrawable shape(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(9));return d;}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER);return r;}
    private TextView label(String text,int size){TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(fg);t.setPadding(dp(10),dp(6),dp(10),dp(6));return t;}
    private Button button(String text,Runnable action){
        Button b=new Button(this);b.setAllCaps(false);b.setText(text);b.setTextSize(15);b.setTextColor(fg);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(0,0,0,0);b.setBackground(shape(keyBg));
        b.setOnClickListener(v->{if(prefs.getBoolean("haptic",true))v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);action.run();});return b;
    }
    private void add(LinearLayout row,Button b,float weight,int height){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(height),weight);p.setMargins(dp(2),dp(3),dp(2),dp(3));row.addView(b,p);}
    private void tool(LinearLayout row,String text,Runnable fn){if(row.getOrientation()==LinearLayout.VERTICAL){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(40));p.setMargins(dp(2),dp(3),dp(2),dp(3));row.addView(button(text,fn),p);}else add(row,button(text,fn),1,38);}
    private void render(){
        if(root==null)return;dismissPopup();root.removeAllViews();letterButtons.clear();
        boolean d=dark();bg=d?0xff202725:0xffe9eee7;fg=d?0xfff0f4ec:0xff243c31;keyBg=d?0xff35423c:0xfffafcf7;accent=d?0xff486957:0xffc7ddbc;
        root.setBackgroundColor(bg);root.setPadding(dp(4),dp(4),dp(4),dp(8));
        LinearLayout toolbar=row();root.addView(toolbar);
        tool(toolbar,"☺",()->{finishWord(false,"");page=page.equals("emoji")?"letters":"emoji";render();});
        if(!privateMode){tool(toolbar,"Clips",()->{finishWord(false,"");page="clips";render();});tool(toolbar,"Voice",()->{finishWord(false,"");page="voice";render();});}
        else tool(toolbar,"Private",()->toast("Learning, clipboard and voice are disabled."));
        tool(toolbar,"Edit",()->{finishWord(false,"");page="edit";render();});
        tool(toolbar,"⚙",()->startActivity(new Intent(this,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        LinearLayout outer=row();root.addView(outer);body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        int hand=prefs.getInt("hand",0);if(hand==2)outer.addView(new View(this),new LinearLayout.LayoutParams(0,1,.2f));
        outer.addView(body,new LinearLayout.LayoutParams(0,-2,hand==0?1:.8f));if(hand==1)outer.addView(new View(this),new LinearLayout.LayoutParams(0,1,.2f));
        if(page.equals("voice")){voicePanel();return;}if(page.equals("clips")){clipsPanel();return;}if(page.equals("emoji")){emojiPanel();return;}if(page.equals("edit")){editPanel();return;}
        candidates=row();body.addView(candidates);suggest();
        GlideLayout keys=new GlideLayout(this);keys.enabled=prefs.getBoolean("glide",false)&&allowWords&&language.equals("en")&&page.equals("letters");keys.listener=this::glide;body.addView(keys);
        int height=new int[]{40,48,56}[Math.min(2,prefs.getInt("height",1))];
        if(getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE)height=36;
        String[][] rows;
        if(page.equals("numbers"))rows=new String[][]{{"1","2","3"},{"4","5","6"},{"7","8","9"},{"+","0",".","-","#","*"}};
        else if(page.equals("symbols"))rows=new String[][]{{"1","2","3","4","5","6","7","8","9","0"},{"@","#","£","_","&","-","+","(",")","/"},{"*","\"","'",":",";","!","?",",",".","="}};
        else if(language.equals("ml"))rows=new String[][]{{"അ","ആ","ഇ","ഈ","ഉ","ഊ","എ","ഏ","ഐ","ഒ","ഓ"},{"ക","ഖ","ഗ","ഘ","ങ","ച","ഛ","ജ","ഞ","ട","ഠ"},{"ഡ","ഢ","ണ","ത","ഥ","ദ","ധ","ന","പ","ഫ","ബ"},{"ഭ","മ","യ","ര","ല","വ","ശ","ഷ","സ","ഹ","ള","ഴ","റ"},{"ാ","ി","ീ","ു","ൂ","ൃ","െ","േ","ൈ","ൊ","ോ","ൗ","്","ം"}};
        else rows=new String[][]{{"q","w","e","r","t","y","u","i","o","p"},{"a","s","d","f","g","h","j","k","l"},{"z","x","c","v","b","n","m"}};
        if(page.equals("letters")&&prefs.getBoolean("numbers",true)&&language.equals("en")){
            LinearLayout r=row();keys.addView(r);for(char c='1';c<='9';c++){String s=""+c;add(r,button(s,()->type(s)),1,34);}add(r,button("0",()->type("0")),1,34);
        }
        for(int i=0;i<rows.length;i++){
            LinearLayout r=row();keys.addView(r);
            if(page.equals("letters")&&language.equals("en")&&i==2){Button shiftKey=button(caps?"⇪":(shift?"⬆":"⇧"),()->{long now=SystemClock.uptimeMillis();if(now-lastShift<350)caps=!caps;else {caps=false;shift=!shift;}lastShift=now;render();});shiftKey.setContentDescription("Shift. Double tap for caps lock");add(r,shiftKey,1.4f,height);}
            for(String key:rows[i]){
                Button b=button(language.equals("en")&&shift?key.toUpperCase(Locale.ROOT):key,()->type(key));add(r,b,1,height);
                if(key.matches("[a-z]")){keys.register(b,key);letterButtons.put(b,key);b.setOnLongClickListener(v->{accents(b,key);return true;});}
            }
            if(i==rows.length-1){Button back=button("⌫",this::backspace);back.setContentDescription("Backspace");back.setOnTouchListener(new View.OnTouchListener(){
                boolean repeated; final Runnable repeat=new Runnable(){public void run(){repeated=true;backspace();main.postDelayed(this,70);}};
                public boolean onTouch(View v,android.view.MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){repeated=false;main.postDelayed(repeat,400);}if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){main.removeCallbacks(repeat);if(repeated)return true;}return false;}
            });add(r,back,1.4f,height);}
        }
        LinearLayout bottom=row();body.addView(bottom);
        add(bottom,button(page.equals("letters")?"?123":"ABC",()->{finishWord(false,"");page=page.equals("letters")?"symbols":"letters";render();}),1.5f,height);
        Button lang=button(language.equals("en")?"EN":"മ",()->{finishWord(false,"");language=language.equals("en")?"ml":"en";page="letters";render();});lang.setOnLongClickListener(v->{((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker();return true;});add(bottom,lang,1,height);
        add(bottom,button(",",()->type(",")),.8f,height);
        Button space=button(privateMode?"Private · space":"space",()->type(" "));space.setContentDescription("Space. Slide left or right to move cursor");
        space.setOnTouchListener(new View.OnTouchListener(){float x;boolean moved;public boolean onTouch(View v,MotionEvent e){if(e.getAction()==MotionEvent.ACTION_DOWN){x=e.getX();moved=false;}if(e.getAction()==MotionEvent.ACTION_MOVE&&Math.abs(e.getX()-x)>dp(18)){finishWord(false,"");keyEvent(e.getX()>x?KeyEvent.KEYCODE_DPAD_RIGHT:KeyEvent.KEYCODE_DPAD_LEFT);x=e.getX();moved=true;}return (e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL)&&moved;}});
        add(bottom,space,4,height);add(bottom,button(".",()->type(".")),.8f,height);add(bottom,button(enterLabel(),this::enter),1.5f,height);
    }
    private void updateShift(){if(!caps){InputConnection ic=getCurrentInputConnection();EditorInfo info=getCurrentInputEditorInfo();shift=!numeric&&ic!=null&&info!=null&&ic.getCursorCapsMode(info.inputType)!=0;}for(Map.Entry<Button,String> e:letterButtons.entrySet())e.getKey().setText(shift||caps?e.getValue().toUpperCase(Locale.ROOT):e.getValue());}
    private void type(String value){
        InputConnection ic=getCurrentInputConnection();if(ic==null)return;undoCommitted="";
        String text=(shift||caps)&&language.equals("en")?value.toUpperCase(Locale.ROOT):value;
        if(allowWords&&language.equals("en")&&text.matches("[a-zA-Z']")){
            if(composing.length()>=48)finishWord(false,"");composing.append(text);ic.setComposingText(composing,1);lastSpace=0;
        }else{
            long now=SystemClock.uptimeMillis();
            if(value.equals(" ")&&allowWords&&prefs.getBoolean("period",true)&&now-lastSpace<500){
                CharSequence before=ic.getTextBeforeCursor(2,0);
                if(before!=null&&before.length()==2&&before.charAt(1)==' '&&Character.isLetterOrDigit(before.charAt(0))){ic.deleteSurroundingText(1,0);ic.commitText(". ",1);lastSpace=0;updateShift();suggest();return;}
            }
            finishWord(value.equals(" ")||value.matches("[.,!?]"),value);
            lastSpace=value.equals(" ")?now:0;
        }
        if(!caps)shift=false;updateShift();suggest();
    }
    private void finishWord(boolean correct,String suffix){
        InputConnection ic=getCurrentInputConnection();if(ic==null){composing.setLength(0);return;}
        if(composing.length()>0){String word=composing.toString(),fixed=correct&&prefs.getBoolean("correct",true)?engine.correction(word):word;ic.commitText(fixed+suffix,1);if(!fixed.equals(word)){undoOriginal=word;undoCommitted=fixed+suffix;}composing.setLength(0);}
        else if(!suffix.isEmpty())ic.commitText(suffix,1);
        ic.finishComposingText();
    }
    private void backspace(){
        InputConnection ic=getCurrentInputConnection();if(ic==null)return;
        if(!undoCommitted.isEmpty()) {CharSequence before=ic.getTextBeforeCursor(undoCommitted.length(),0);if(before!=null&&before.toString().equals(undoCommitted)){ic.beginBatchEdit();ic.deleteSurroundingText(undoCommitted.length(),0);ic.commitText(undoOriginal,1);ic.endBatchEdit();undoCommitted="";updateShift();suggest();return;}undoCommitted="";}
        if(composing.length()>0){composing.deleteCharAt(composing.length()-1);ic.setComposingText(composing,1);if(composing.length()==0)ic.finishComposingText();}
        else {CharSequence selected=ic.getSelectedText(0);if(selected!=null&&selected.length()>0)ic.commitText("",1);else {CharSequence before=ic.getTextBeforeCursor(128,0);if(before!=null&&before.length()>0){BreakIterator iterator=BreakIterator.getCharacterInstance();iterator.setText(before.toString());int boundary=iterator.preceding(before.length());ic.deleteSurroundingText(before.length()-Math.max(0,boundary),0);}else keyEvent(KeyEvent.KEYCODE_DEL);}}
        updateShift();suggest();
    }
    private void suggest(){if(candidates==null)return;candidates.removeAllViews();if(!allowWords||!page.equals("letters")||!language.equals("en")){candidates.addView(label(privateMode?"Private typing":"Typer",13));return;}for(String candidate:engine.suggestions(composing.toString()))tool(candidates,candidate,()->{InputConnection ic=getCurrentInputConnection();if(ic!=null){ic.commitText(candidate+" ",1);ic.finishComposingText();composing.setLength(0);updateShift();suggest();}});}
    private void glide(String trace){List<String> options=engine.glide(trace);if(options.isEmpty()){toast("No swipe match. Try tapping this word.");return;}finishWord(false,"");String word=shift?TypingEngine.matchCase("A",options.get(0)):options.get(0);composing.append(word);if(getCurrentInputConnection()!=null)getCurrentInputConnection().setComposingText(word,1);candidates.removeAllViews();for(String option:options)tool(candidates,option,()->{composing.setLength(0);if(getCurrentInputConnection()!=null)getCurrentInputConnection().commitText(option+" ",1);suggest();updateShift();});}
    private String enterLabel(){EditorInfo i=getCurrentInputEditorInfo();if(i==null||(i.imeOptions&EditorInfo.IME_FLAG_NO_ENTER_ACTION)!=0)return "↵";switch(i.imeOptions&EditorInfo.IME_MASK_ACTION){case EditorInfo.IME_ACTION_SEARCH:return "Search";case EditorInfo.IME_ACTION_SEND:return "Send";case EditorInfo.IME_ACTION_GO:return "Go";case EditorInfo.IME_ACTION_NEXT:return "Next";case EditorInfo.IME_ACTION_DONE:return "Done";default:return "↵";}}
    private void enter(){finishWord(false,"");InputConnection ic=getCurrentInputConnection();EditorInfo info=getCurrentInputEditorInfo();if(ic==null||info==null)return;int action=info.imeOptions&EditorInfo.IME_MASK_ACTION;if((info.imeOptions&EditorInfo.IME_FLAG_NO_ENTER_ACTION)==0&&action!=EditorInfo.IME_ACTION_NONE&&action!=EditorInfo.IME_ACTION_UNSPECIFIED){ic.performEditorAction(action);}else ic.commitText("\n",1);}
    private void keyEvent(int code){InputConnection ic=getCurrentInputConnection();if(ic!=null){ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,code));ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,code));}}
    private void backToKeys(){page=numeric?"numbers":"letters";render();}
    private void editPanel(){body.addView(label("Text tools",20));LinearLayout r=row();body.addView(r);tool(r,"←",()->keyEvent(KeyEvent.KEYCODE_DPAD_LEFT));tool(r,"↑",()->keyEvent(KeyEvent.KEYCODE_DPAD_UP));tool(r,"↓",()->keyEvent(KeyEvent.KEYCODE_DPAD_DOWN));tool(r,"→",()->keyEvent(KeyEvent.KEYCODE_DPAD_RIGHT));LinearLayout actions=row();body.addView(actions);tool(actions,"Select all",()->context(android.R.id.selectAll));if(!privateMode){tool(actions,"Copy",()->context(android.R.id.copy));tool(actions,"Cut",()->context(android.R.id.cut));tool(actions,"Paste",()->context(android.R.id.paste));}tool(body,"Back to keys",this::backToKeys);}
    private void context(int id){InputConnection ic=getCurrentInputConnection();if(ic!=null)ic.performContextMenuAction(id);}
    private void emojiPanel(){body.addView(label("Emoji",20));String[] emoji="😀 😃 😄 😁 😂 🥹 😊 😍 🥰 😎 🤔 😭 😤 😴 🫶 👍 👎 👏 🙏 💪 ❤️ 💚 💙 🔥 ✨ 🎉 🎮 📚 ☕ 🌍 🐶 🌞".split(" ");for(int i=0;i<emoji.length;i+=8){LinearLayout r=row();body.addView(r);for(int j=i;j<Math.min(i+8,emoji.length);j++){String s=emoji[j];tool(r,s,()->{if(getCurrentInputConnection()!=null)getCurrentInputConnection().commitText(s,1);});}}tool(body,"Back to keys",this::backToKeys);}
    private void clipsPanel(){
        if(privateMode){backToKeys();return;}body.addView(label("Clipboard · tap to paste",18));
        ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);String current="";
        if(clipboard.hasPrimaryClip()&&clipboard.getPrimaryClip()!=null&&clipboard.getPrimaryClip().getItemCount()>0){CharSequence value=clipboard.getPrimaryClip().getItemAt(0).getText();if(value!=null)current=value.toString();}
        final String clip=current;JSONArray saved;try{saved=new JSONArray(prefs.getString("clips","[]"));}catch(Exception ex){saved=new JSONArray();}final JSONArray clips=saved;
        if(!clip.isEmpty()){tool(body,clip.length()>65?clip.substring(0,65)+"…":clip,()->{if(getCurrentInputConnection()!=null)getCurrentInputConnection().commitText(clip,1);});tool(body,"Save current clip",()->{if(clip.length()>4000){toast("Clip is too long to save.");return;}JSONArray next=new JSONArray();next.put(clip);for(int i=0;i<Math.min(clips.length(),9);i++)if(!clips.optString(i).equals(clip))next.put(clips.optString(i));prefs.edit().putString("clips",next.toString()).apply();render();});}
        else body.addView(label("Nothing to paste. Copy some text first.",14));
        ScrollView scroll=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list);body.addView(scroll,new LinearLayout.LayoutParams(-1,dp(100)));
        for(int i=0;i<clips.length();i++){String s=clips.optString(i);Button b=button(s.length()>65?s.substring(0,65)+"…":s,()->{if(getCurrentInputConnection()!=null)getCurrentInputConnection().commitText(s,1);});list.addView(b);}
        tool(body,"Clear saved clips",()->{prefs.edit().remove("clips").apply();render();});tool(body,"Back to keys",this::backToKeys);
    }
    private void accents(Button anchor,String key){String variants;switch(key){case "a":variants="á à â ä æ ã å";break;case "e":variants="é è ê ë";break;case "i":variants="í ì î ï";break;case "o":variants="ó ò ô ö ø õ";break;case "u":variants="ú ù û ü";break;case "n":variants="ñ";break;case "c":variants="ç";break;default:variants=key.toUpperCase(Locale.ROOT);}
        LinearLayout r=row();r.setBackgroundColor(bg);popup=new PopupWindow(r,-2,dp(56),true);popup.setBackgroundDrawable(shape(bg));popup.setOutsideTouchable(true);for(String s:variants.split(" ")){Button b=button(s,()->{finishWord(false,"");if(getCurrentInputConnection()!=null)getCurrentInputConnection().commitText(s,1);dismissPopup();});r.addView(b,new LinearLayout.LayoutParams(dp(38),dp(48)));}popup.showAsDropDown(anchor,0,-dp(105));}
    private void dismissPopup(){if(popup!=null){popup.dismiss();popup=null;}}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}

    private void voicePanel(){
        if(privateMode){backToKeys();return;}body.addView(label("Rambler-style voice",21));
        body.addView(label(voiceStatus.isEmpty()?"Speak naturally. Review, then insert.":voiceStatus,13));
        ScrollView scroll=new ScrollView(this);TextView preview=label(draft.isEmpty()?"Your draft will appear here…":draft,18);preview.setTextIsSelectable(true);scroll.addView(preview);body.addView(scroll,new LinearLayout.LayoutParams(-1,dp(112)));
        LinearLayout record=row();body.addView(record);tool(record,listening?"Stop":"Dictate",()->{if(listening){if(speech!=null)speech.stopListening();}else listen(false);});
        if(!draft.isEmpty()) {Button edit=button("Voice edit",()->listen(true));edit.setEnabled(!busy&&!listening);add(record,edit,1,38);}
        LinearLayout actions=row();body.addView(actions);
        Button basic=button("Basic cleanup",()->{previousDraft=draft;draft=TypingEngine.basicCleanup(draft);voiceStatus="Local punctuation and filler cleanup";render();});basic.setEnabled(!draft.isEmpty()&&!busy&&!listening);add(actions,basic,1,38);
        Button ai=button("AI polish ↗",()->rewrite("Polish the draft. Remove filler and repetitions, resolve explicit self-corrections, and improve punctuation without changing its meaning or language."));ai.setEnabled(!draft.isEmpty()&&!busy&&!listening);add(actions,ai,1,38);
        LinearLayout last=row();body.addView(last);tool(last,"Undo",()->{if(!busy&&!listening){String temp=draft;draft=previousDraft;previousDraft=temp;render();}});
        Button insert=button("Insert",()->{if(!busy&&!listening&&!draft.isEmpty()&&getCurrentInputConnection()!=null){getCurrentInputConnection().commitText(draft,1);resetVoice();backToKeys();}});insert.setBackground(shape(accent));insert.setEnabled(!draft.isEmpty()&&!busy&&!listening);add(last,insert,1,38);
        tool(last,"Cancel",()->{resetVoice();backToKeys();});
    }
    private void listen(boolean edit){
        if(privateMode||busy||listening)return;
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){toast("Allow microphone access in Typer settings first.");return;}
        if(edit&&(prefs.getString("endpoint","").isEmpty()||draft.isEmpty())){toast("Configure your AI server in settings to use spoken edits.");return;}
        if(speech!=null){speech.destroy();speech=null;}
        boolean onDevice=Build.VERSION.SDK_INT>=31&&SpeechRecognizer.isOnDeviceRecognitionAvailable(this);
        if(onDevice)speech=SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
        else if(prefs.getBoolean("networkSpeech",false)&&SpeechRecognizer.isRecognitionAvailable(this))speech=SpeechRecognizer.createSpeechRecognizer(this);
        else{voiceStatus="On-device speech unavailable. Download a speech language pack, or enable system speech in settings.";render();return;}
        final int active=session,request=++voiceRequest;
        speech.setRecognitionListener(new RecognitionListener(){
            private boolean valid(){return active==session&&request==voiceRequest;}
            public void onReadyForSpeech(Bundle b){}public void onBeginningOfSpeech(){}public void onRmsChanged(float f){}public void onBufferReceived(byte[] b){}public void onEvent(int t,Bundle b){}
            public void onEndOfSpeech(){if(valid()){voiceStatus="Transcribing…";render();}}
            public void onPartialResults(Bundle results){if(!valid())return;ArrayList<String> lines=results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(lines!=null&&!lines.isEmpty()){voiceStatus=lines.get(0);render();}}
            public void onError(int code){if(!valid())return;listening=false;voiceStatus="Speech stopped ("+code+"). Tap Dictate to retry; your draft is kept.";render();}
            public void onResults(Bundle results){if(!valid())return;listening=false;ArrayList<String> lines=results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(lines==null||lines.isEmpty()){voiceStatus="No speech heard.";render();return;}String text=lines.get(0);if(edit){rewrite(text);}else{previousDraft=draft;raw=text;draft=(draft.isEmpty()?"":draft+" ")+text;voiceStatus="Review your words, then clean up or insert.";render();}}
        });
        Intent intent=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,language.equals("ml")?"ml-IN":"en-GB");intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
        listening=true;voiceStatus=edit?"Say how to change this draft. It will be sent to your AI server.":"Listening"+(onDevice?" · on device":" · system speech provider");render();
        try{speech.startListening(intent);}catch(RuntimeException ex){listening=false;voiceStatus="Speech service could not start. Check permissions and language support.";render();}
    }
    private void rewrite(String instruction){
        if(privateMode||draft.isEmpty()||busy)return;String endpoint=prefs.getString("endpoint","");if(endpoint.isEmpty()){toast("Configure your AI server in settings, or use Basic cleanup.");return;}
        String token;try{token=Prefs.token(this);}catch(Exception ex){toast("Save your server access token again in settings.");return;}
        final String source=draft;final int active=session;busy=true;voiceStatus="Sending this draft to your AI server…";render();
        worker.execute(()->{try{String result=RamblerClient.rewrite(endpoint,token,source,instruction);main.post(()->{if(active!=session)return;busy=false;previousDraft=source;draft=result;voiceStatus="AI draft · check names, numbers and meaning before inserting.";render();});}catch(Exception ex){main.post(()->{if(active!=session)return;busy=false;voiceStatus="AI unavailable. Your draft is unchanged. Check the server connection.";render();});}});
    }
}
