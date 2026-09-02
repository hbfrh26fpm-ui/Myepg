package com.myepg.player;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.xmlpull.v1.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import java.util.zip.GZIPInputStream;

public class MainActivity extends Activity {
  static final int REQ_M3U=10, REQ_EPG=11;
  static final String PREFS="myepg", P_M3U_URL="m3u_url", P_M3U_URI="m3u_uri", P_EPG_URLS="epg_urls", P_EPG_URIS="epg_uris", P_FAV="fav";
  final ExecutorService io=Executors.newSingleThreadExecutor();
  final Handler main=new Handler(Looper.getMainLooper());
  final List<Channel> channels=new ArrayList<>(), shown=new ArrayList<>();
  final Map<String,List<Programme>> epgId=new HashMap<>(), epgName=new HashMap<>();
  SharedPreferences prefs; Set<String> favs=new HashSet<>(); ListView list; ArrayAdapter<Channel> adapter; VideoView video;
  TextView title,now,next,status; ProgressBar spinner; String filter="";

  @Override public void onCreate(Bundle b){super.onCreate(b); prefs=getSharedPreferences(PREFS,MODE_PRIVATE); favs=new HashSet<>(prefs.getStringSet(P_FAV,new HashSet<>())); buildUi(); restore();}

  void buildUi(){
    LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(16),dp(12),dp(16),dp(12)); root.setBackgroundColor(Color.rgb(8,10,15));
    LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
    ImageView logo=new ImageView(this); logo.setImageResource(R.drawable.myepg_logo); logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE); top.addView(logo,new LinearLayout.LayoutParams(dp(210),dp(64)));
    Button m3uUrl=btn("M3U URL"), m3uFile=btn("M3U file"), epgUrl=btn("EPG URL"), epgFile=btn("EPG file"), search=btn("Cerca"), fav=btn("Preferiti"), refresh=btn("Aggiorna");
    for(Button x:new Button[]{m3uUrl,m3uFile,epgUrl,epgFile,search,fav,refresh}) top.addView(x); root.addView(top,new LinearLayout.LayoutParams(-1,dp(72)));
    LinearLayout body=new LinearLayout(this); body.setOrientation(LinearLayout.HORIZONTAL);
    list=new ListView(this); list.setBackgroundColor(Color.rgb(13,17,24)); adapter=new ArrayAdapter<Channel>(this,android.R.layout.simple_list_item_1,shown){@Override public View getView(int p,View v,ViewGroup g){TextView t=(TextView)super.getView(p,v,g); Channel c=getItem(p); t.setText((favs.contains(c.key())?"★  ":"")+c.name+(c.group.isEmpty()?"":"\n"+c.group)); t.setTextColor(Color.WHITE); t.setTextSize(17); t.setPadding(dp(14),dp(10),dp(10),dp(10)); t.setBackgroundColor(Color.rgb(13,17,24)); return t;}}; list.setAdapter(adapter); body.addView(list,new LinearLayout.LayoutParams(0,-1,.34f));
    LinearLayout panel=new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL); panel.setPadding(dp(14),0,0,0); video=new VideoView(this); video.setBackgroundColor(Color.BLACK); panel.addView(video,new LinearLayout.LayoutParams(-1,0,.72f));
    title=label("Seleziona un canale",24,true); now=label("Ora: —",18,false); next=label("Dopo: —",16,false); status=label("Aggiungi una playlist M3U per iniziare.",14,false); spinner=new ProgressBar(this); spinner.setVisibility(View.GONE); panel.addView(title); panel.addView(now); panel.addView(next); LinearLayout st=new LinearLayout(this); st.setGravity(Gravity.CENTER_VERTICAL); st.addView(spinner,new LinearLayout.LayoutParams(dp(34),dp(34))); st.addView(status,new LinearLayout.LayoutParams(0,-2,1)); panel.addView(st); body.addView(panel,new LinearLayout.LayoutParams(0,-1,.66f)); root.addView(body,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
    m3uUrl.setOnClickListener(v->prompt("URL playlist M3U/M3U8",prefs.getString(P_M3U_URL,""),s->{prefs.edit().putString(P_M3U_URL,s).remove(P_M3U_URI).apply(); loadM3uUrl(s);}));
    m3uFile.setOnClickListener(v->pick(REQ_M3U)); epgUrl.setOnClickListener(v->prompt("URL EPG XMLTV (.xml/.gz)","",s->{Set<String>x=new HashSet<>(prefs.getStringSet(P_EPG_URLS,new HashSet<>()));x.add(s);prefs.edit().putStringSet(P_EPG_URLS,x).apply();loadEpg();})); epgFile.setOnClickListener(v->pick(REQ_EPG));
    search.setOnClickListener(v->prompt("Cerca canale",filter.startsWith("__")?"":filter,s->{filter=s;refreshList();})); fav.setOnClickListener(v->{filter="__FAV__";refreshList();}); refresh.setOnClickListener(v->restore());
    list.setOnItemClickListener((a,v,p,id)->play(shown.get(p))); list.setOnItemLongClickListener((a,v,p,id)->{toggleFav(shown.get(p));return true;}); list.requestFocus();
  }

  Button btn(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(12);b.setFocusable(true);return b;} TextView label(String s,int sp,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(sp);t.setPadding(0,dp(4),0,dp(3));if(bold)t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);return t;} int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
  interface Consumer{void run(String s);} void prompt(String title,String initial,Consumer c){EditText e=new EditText(this);e.setSingleLine();e.setText(initial);e.setSelection(e.length());new AlertDialog.Builder(this).setTitle(title).setView(e).setPositiveButton("OK",(d,w)->{String s=e.getText().toString().trim();if(!s.isEmpty())c.run(s);}).setNegativeButton("Annulla",null).show();}
  void pick(int req){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,req);}
  @Override protected void onActivityResult(int r,int result,Intent data){super.onActivityResult(r,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri u=data.getData();try{getContentResolver().takePersistableUriPermission(u,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}if(r==REQ_M3U){prefs.edit().putString(P_M3U_URI,u.toString()).remove(P_M3U_URL).apply();loadM3uUri(u);}else if(r==REQ_EPG){Set<String>x=new HashSet<>(prefs.getStringSet(P_EPG_URIS,new HashSet<>()));x.add(u.toString());prefs.edit().putStringSet(P_EPG_URIS,x).apply();loadEpg();}}
  void restore(){String u=prefs.getString(P_M3U_URL,"");String f=prefs.getString(P_M3U_URI,"");if(!u.isEmpty())loadM3uUrl(u);else if(!f.isEmpty())loadM3uUri(Uri.parse(f));loadEpg();}

  void loadM3uUrl(String s){busy("Carico playlist…");io.execute(()->{try{applyChannels(parseM3u(readText(openHttp(s))));}catch(Exception e){fail("Playlist: "+e.getMessage());}});} void loadM3uUri(Uri u){busy("Leggo playlist…");io.execute(()->{try(InputStream in=getContentResolver().openInputStream(u)){applyChannels(parseM3u(readText(in)));}catch(Exception e){fail("Playlist: "+e.getMessage());}});} void applyChannels(List<Channel> x){main.post(()->{channels.clear();channels.addAll(x);filter="";refreshList();idle(channels.size()+" canali caricati");});}
  List<Channel> parseM3u(String text){List<Channel> out=new ArrayList<>();String[] lines=text.replace("\r","").split("\n");Pattern a=Pattern.compile("([A-Za-z0-9_-]+)=\"([^\"]*)\"");for(int i=0;i<lines.length;i++){String line=lines[i].trim();if(!line.startsWith("#EXTINF"))continue;String url="";for(int j=i+1;j<lines.length;j++){String n=lines[j].trim();if(n.isEmpty())continue;if(!n.startsWith("#")){url=n;i=j;}break;}if(url.isEmpty())continue;Channel c=new Channel();c.url=url;int comma=line.lastIndexOf(',');c.name=comma>=0?line.substring(comma+1).trim():"Canale";Matcher m=a.matcher(line);while(m.find()){String k=m.group(1).toLowerCase(Locale.US),v=m.group(2);if(k.equals("tvg-id"))c.id=v;else if(k.equals("tvg-name"))c.tvgName=v;else if(k.equals("group-title"))c.group=v;}if(c.name.isEmpty())c.name=c.tvgName.isEmpty()?"Canale":c.tvgName;out.add(c);}return out;}

  void loadEpg(){busy("Aggiorno EPG…");io.execute(()->{Map<String,List<Programme>> ids=new HashMap<>(),names=new HashMap<>();int ok=0;for(String s:prefs.getStringSet(P_EPG_URLS,new HashSet<>()))try(InputStream in=maybeGzip(openHttp(s),s)){parseXml(in,ids,names);ok++;}catch(Exception ignored){}for(String s:prefs.getStringSet(P_EPG_URIS,new HashSet<>()))try(InputStream raw=getContentResolver().openInputStream(Uri.parse(s));InputStream in=maybeGzip(raw,s)){parseXml(in,ids,names);ok++;}catch(Exception ignored){}final int count=ok;main.post(()->{epgId.clear();epgId.putAll(ids);epgName.clear();epgName.putAll(names);spinner.setVisibility(View.GONE);status.setText(count+" sorgenti EPG caricate");});});}
  void parseXml(InputStream in,Map<String,List<Programme>> ids,Map<String,List<Programme>> names)throws Exception{XmlPullParser p=XmlPullParserFactory.newInstance().newPullParser();p.setInput(in,"UTF-8");Map<String,String> channelNames=new HashMap<>();String cid=null;for(int e=p.getEventType();e!=XmlPullParser.END_DOCUMENT;e=p.next()){if(e==XmlPullParser.START_TAG&&"channel".equals(p.getName()))cid=safe(p.getAttributeValue(null,"id"));else if(e==XmlPullParser.START_TAG&&"display-name".equals(p.getName())&&cid!=null){String n=p.nextText().trim();if(!n.isEmpty())channelNames.put(cid,n);}else if(e==XmlPullParser.END_TAG&&"channel".equals(p.getName()))cid=null;else if(e==XmlPullParser.START_TAG&&"programme".equals(p.getName())){Programme pr=new Programme();pr.channel=safe(p.getAttributeValue(null,"channel"));pr.start=date(p.getAttributeValue(null,"start"));pr.stop=date(p.getAttributeValue(null,"stop"));int depth=1;while(depth>0){int x=p.next();if(x==XmlPullParser.START_TAG){depth++;if("title".equals(p.getName())){pr.title=p.nextText();depth--;}}else if(x==XmlPullParser.END_TAG)depth--;}if(!pr.channel.isEmpty()&&pr.start>0)ids.computeIfAbsent(pr.channel,k->new ArrayList<>()).add(pr);}}for(Map.Entry<String,String> e:channelNames.entrySet()){List<Programme> l=ids.get(e.getKey());if(l!=null)names.put(norm(e.getValue()),l);}for(List<Programme> l:ids.values())Collections.sort(l,Comparator.comparingLong(a->a.start));}
  long date(String raw){if(raw==null)return 0;String[] p=raw.trim().split("\\s+");String stamp=p[0],zone=p.length>1?p[1]:"+0000";try{return new SimpleDateFormat("yyyyMMddHHmmss Z",Locale.US).parse(stamp+" "+zone).getTime();}catch(Exception e){return 0;}}
  InputStream openHttp(String s)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(s).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setRequestProperty("User-Agent","MYEPG Player/0.1");return c.getInputStream();}
  InputStream maybeGzip(InputStream raw,String name)throws Exception{BufferedInputStream b=new BufferedInputStream(raw);b.mark(4);int a=b.read(),c=b.read();b.reset();return name.toLowerCase(Locale.US).endsWith(".gz")||(a==0x1f&&c==0x8b)?new GZIPInputStream(b):b;} String readText(InputStream in)throws Exception{try(BufferedReader r=new BufferedReader(new InputStreamReader(in))){StringBuilder b=new StringBuilder();String s;while((s=r.readLine())!=null)b.append(s).append('\n');return b.toString();}}

  void refreshList(){shown.clear();String q=filter.toLowerCase(Locale.US);for(Channel c:channels)if("__FAV__".equals(filter)?favs.contains(c.key()):q.isEmpty()||c.name.toLowerCase(Locale.US).contains(q)||c.group.toLowerCase(Locale.US).contains(q))shown.add(c);adapter.notifyDataSetChanged();status.setText(shown.size()+" / "+channels.size()+" canali");}
  void play(Channel c){title.setText(c.name+(favs.contains(c.key())?"  ★":""));showProgramme(c);try{video.stopPlayback();video.setVideoURI(Uri.parse(c.url));video.start();status.setText("Riproduzione: "+c.name);}catch(Exception e){fail("Player: "+e.getMessage());}}
  void showProgramme(Channel c){List<Programme> l=!c.id.isEmpty()?epgId.get(c.id):null;if(l==null)l=epgName.get(norm(c.tvgName.isEmpty()?c.name:c.tvgName));long n=System.currentTimeMillis();Programme cur=null,nxt=null;if(l!=null)for(Programme p:l){if(p.start<=n&&(p.stop==0||p.stop>n))cur=p;else if(p.start>n){nxt=p;break;}}now.setText("Ora: "+fmt(cur));next.setText("Dopo: "+fmt(nxt));}
  String fmt(Programme p){if(p==null)return "—";SimpleDateFormat f=new SimpleDateFormat("HH:mm",Locale.getDefault());return f.format(new Date(p.start))+(p.stop>0?"–"+f.format(new Date(p.stop)):"")+"  "+(p.title.isEmpty()?"Senza titolo":p.title);}
  void toggleFav(Channel c){if(favs.contains(c.key()))favs.remove(c.key());else favs.add(c.key());prefs.edit().putStringSet(P_FAV,new HashSet<>(favs)).apply();adapter.notifyDataSetChanged();}
  void busy(String s){main.post(()->{spinner.setVisibility(View.VISIBLE);status.setText(s);});} void idle(String s){spinner.setVisibility(View.GONE);status.setText(s);} void fail(String s){main.post(()->{spinner.setVisibility(View.GONE);status.setText(s);Toast.makeText(this,s,Toast.LENGTH_LONG).show();});}
  static String safe(String s){return s==null?"":s;} static String norm(String s){return safe(s).toLowerCase(Locale.US).replaceAll("[^a-z0-9]+","");}
  @Override public boolean dispatchKeyEvent(KeyEvent e){if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getKeyCode()==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE){if(video.isPlaying())video.pause();else video.start();return true;}return super.dispatchKeyEvent(e);} @Override protected void onDestroy(){super.onDestroy();io.shutdownNow();try{video.stopPlayback();}catch(Exception ignored){}}
  static class Channel{String id="",tvgName="",name="",group="",url="";String key(){return !id.isEmpty()?id:name+"|"+url;} @Override public String toString(){return name;}}
  static class Programme{String channel="",title="";long start,stop;}
}
