package com.myepg.player;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

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
  static final String PREFS="myepg", P_SOURCE="source", P_M3U_URL="m3u_url", P_M3U_URI="m3u_uri", P_EPG_URLS="epg_urls", P_EPG_URIS="epg_uris", P_FAV="fav", P_RECENT="recent", P_X_SERVER="x_server", P_X_USER="x_user", P_X_PASS="x_pass", P_X_EPG="x_epg";
  static final int MODE_ALL=0, MODE_FAV=1, MODE_RECENT=2;

  final ExecutorService io=Executors.newSingleThreadExecutor();
  final Handler main=new Handler(Looper.getMainLooper());
  final List<Channel> channels=new ArrayList<>(), shown=new ArrayList<>(), categoryRows=new ArrayList<>();
  final Map<String,List<Programme>> epgId=new HashMap<>(), epgName=new HashMap<>();

  SharedPreferences prefs; Set<String> favs=new HashSet<>();
  ListView categoryList, channelList; BaseAdapter categoryAdapter, channelAdapter;
  PlayerView playerView; ExoPlayer player;
  TextView header, subtitle, title, now, next, status, channelMeta;
  ProgressBar spinner;
  String selectedCategory="Tutti", query=""; int mode=MODE_ALL;

  final int bg=Color.rgb(7,9,13), panel=Color.rgb(14,18,25), panel2=Color.rgb(19,24,33), text=Color.rgb(242,245,250), muted=Color.rgb(150,160,176), focus=Color.rgb(39,60,96);

  @Override public void onCreate(Bundle b){super.onCreate(b);prefs=getSharedPreferences(PREFS,MODE_PRIVATE);favs=new HashSet<>(prefs.getStringSet(P_FAV,new HashSet<>()));buildUi();initPlayer();restore();}

  void initPlayer(){
    DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setUserAgent("MYEPG Player/0.3").setConnectTimeoutMs(15000).setReadTimeoutMs(30000).setAllowCrossProtocolRedirects(true);
    player=new ExoPlayer.Builder(this).setMediaSourceFactory(new DefaultMediaSourceFactory(http)).build();playerView.setPlayer(player);
    player.addListener(new Player.Listener(){@Override public void onPlayerError(PlaybackException e){fail("Player: "+e.getErrorCodeName());}@Override public void onPlaybackStateChanged(int s){if(s==Player.STATE_BUFFERING)status.setText("Buffering…");else if(s==Player.STATE_READY)status.setText("In riproduzione");}});
  }

  void buildUi(){
    LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.HORIZONTAL);root.setBackgroundColor(bg);
    LinearLayout nav=new LinearLayout(this);nav.setOrientation(LinearLayout.VERTICAL);nav.setPadding(dp(14),dp(18),dp(14),dp(14));nav.setBackgroundColor(Color.rgb(9,12,18));
    ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.myepg_logo);logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);nav.addView(logo,new LinearLayout.LayoutParams(-1,dp(72)));
    TextView version=txt("MYEPG PLAYER  ·  v0.3",11,muted,true);version.setGravity(Gravity.CENTER);nav.addView(version,new LinearLayout.LayoutParams(-1,dp(34)));
    Button live=navButton("▣  Live TV"),fav=navButton("★  Preferiti"),recent=navButton("↻  Recenti"),search=navButton("⌕  Cerca"),playlists=navButton("＋  Playlist"),epg=navButton("≡  EPG"),refresh=navButton("⟳  Aggiorna");
    for(Button b:new Button[]{live,fav,recent,search,playlists,epg,refresh})nav.addView(b,new LinearLayout.LayoutParams(-1,dp(54)));
    nav.addView(new Space(this),new LinearLayout.LayoutParams(1,0,1));TextView hint=txt("OK  riproduci\nPressione lunga  preferito\n▶⏸  play / pausa",12,muted,false);hint.setLineSpacing(0,1.25f);nav.addView(hint);root.addView(nav,new LinearLayout.LayoutParams(dp(190),-1));

    LinearLayout mainArea=new LinearLayout(this);mainArea.setOrientation(LinearLayout.VERTICAL);mainArea.setPadding(dp(18),dp(16),dp(18),dp(16));
    LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);LinearLayout headWrap=new LinearLayout(this);headWrap.setOrientation(LinearLayout.VERTICAL);header=txt("Live TV",28,text,true);subtitle=txt("Nessuna playlist caricata",13,muted,false);headWrap.addView(header);headWrap.addView(subtitle);top.addView(headWrap,new LinearLayout.LayoutParams(0,dp(64),1));spinner=new ProgressBar(this);spinner.setVisibility(View.GONE);top.addView(spinner,new LinearLayout.LayoutParams(dp(34),dp(34)));mainArea.addView(top,new LinearLayout.LayoutParams(-1,dp(72)));

    LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.HORIZONTAL);
    LinearLayout catPanel=columnPanel("CATEGORIE");categoryList=new ListView(this);categoryList.setDividerHeight(0);categoryList.setSelector(android.R.color.transparent);catPanel.addView(categoryList,new LinearLayout.LayoutParams(-1,0,1));body.addView(catPanel,new LinearLayout.LayoutParams(dp(210),-1));body.addView(new Space(this),new LinearLayout.LayoutParams(dp(12),1));
    LinearLayout chanPanel=columnPanel("CANALI");channelList=new ListView(this);channelList.setDividerHeight(0);channelList.setSelector(android.R.color.transparent);chanPanel.addView(channelList,new LinearLayout.LayoutParams(-1,0,1));body.addView(chanPanel,new LinearLayout.LayoutParams(dp(350),-1));body.addView(new Space(this),new LinearLayout.LayoutParams(dp(14),1));
    LinearLayout playerPanel=new LinearLayout(this);playerPanel.setOrientation(LinearLayout.VERTICAL);playerPanel.setPadding(dp(14),dp(14),dp(14),dp(14));playerPanel.setBackground(round(panel,16));
    playerView=new PlayerView(this);playerView.setBackgroundColor(Color.BLACK);playerView.setUseController(true);playerView.setControllerAutoShow(false);playerPanel.addView(playerView,new LinearLayout.LayoutParams(-1,0,.66f));
    title=txt("Seleziona un canale",25,text,true);title.setPadding(0,dp(14),0,0);channelMeta=txt("MYEPG Player",12,muted,false);now=txt("ORA  —",17,text,false);next=txt("DOPO  —",15,muted,false);status=txt("Aggiungi una playlist M3U o Xtream per iniziare.",13,muted,false);now.setPadding(0,dp(12),0,dp(5));next.setPadding(0,0,0,dp(8));playerPanel.addView(title);playerPanel.addView(channelMeta);playerPanel.addView(now);playerPanel.addView(next);playerPanel.addView(status);body.addView(playerPanel,new LinearLayout.LayoutParams(0,-1,1));mainArea.addView(body,new LinearLayout.LayoutParams(-1,0,1));root.addView(mainArea,new LinearLayout.LayoutParams(0,-1,1));setContentView(root);

    categoryAdapter=new BaseAdapter(){public int getCount(){return categoryRows.size();}public Object getItem(int p){return categoryRows.get(p);}public long getItemId(int p){return p;}public View getView(int p,View v,ViewGroup g){return row(categoryRows.get(p).name,15);}};categoryList.setAdapter(categoryAdapter);
    channelAdapter=new BaseAdapter(){public int getCount(){return shown.size();}public Object getItem(int p){return shown.get(p);}public long getItemId(int p){return p;}public View getView(int p,View v,ViewGroup g){Channel c=shown.get(p);LinearLayout w=new LinearLayout(MainActivity.this);w.setOrientation(LinearLayout.VERTICAL);w.setPadding(dp(14),dp(10),dp(12),dp(10));w.setBackground(round(panel2,11));w.setFocusable(true);TextView a=txt((favs.contains(c.key())?"★  ":"")+c.name,16,text,true);TextView b=txt(c.group.isEmpty()?"Live TV":c.group,12,muted,false);w.addView(a);w.addView(b);applyFocus(w);return w;}};channelList.setAdapter(channelAdapter);
    categoryList.setOnItemClickListener((a,v,p,id)->{selectedCategory=categoryRows.get(p).name;refreshChannels();});channelList.setOnItemClickListener((a,v,p,id)->play(shown.get(p)));channelList.setOnItemLongClickListener((a,v,p,id)->{toggleFav(shown.get(p));return true;});
    live.setOnClickListener(v->{mode=MODE_ALL;query="";selectedCategory="Tutti";header.setText("Live TV");refreshCategories();refreshChannels();});fav.setOnClickListener(v->{mode=MODE_FAV;query="";selectedCategory="Tutti";header.setText("Preferiti");refreshCategories();refreshChannels();});recent.setOnClickListener(v->{mode=MODE_RECENT;query="";selectedCategory="Tutti";header.setText("Recenti");refreshCategories();refreshChannels();});search.setOnClickListener(v->prompt("Cerca canale","",s->{query=s;mode=MODE_ALL;selectedCategory="Tutti";header.setText("Ricerca");refreshCategories();refreshChannels();}));playlists.setOnClickListener(v->showPlaylistDialog());epg.setOnClickListener(v->showEpgDialog());refresh.setOnClickListener(v->restore());live.requestFocus();
  }

  LinearLayout columnPanel(String label){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(8),dp(10),dp(8),dp(8));x.setBackground(round(panel,14));TextView h=txt(label,11,muted,true);h.setPadding(dp(8),0,0,dp(8));x.addView(h,new LinearLayout.LayoutParams(-1,dp(32)));return x;}
  TextView row(String s,int sp){TextView t=txt(s,sp,text,false);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(dp(14),dp(8),dp(10),dp(8));t.setMinHeight(dp(48));t.setBackground(round(panel2,10));t.setFocusable(true);applyFocus(t);return t;}
  Button navButton(String s){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextColor(text);b.setTextSize(14);b.setGravity(Gravity.CENTER_VERTICAL);b.setPadding(dp(16),0,dp(8),0);b.setBackground(round(panel2,11));b.setFocusable(true);applyFocus(b);return b;}
  TextView txt(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(sp);if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);return t;}
  GradientDrawable round(int color,int r){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(r));return d;}
  void applyFocus(View v){v.setOnFocusChangeListener((x,f)->x.setBackground(round(f?focus:panel2,11)));}
  int dp(int x){return(int)(x*getResources().getDisplayMetrics().density+.5f);}

  interface Consumer{void run(String s);}void prompt(String title,String initial,Consumer c){EditText e=input(initial,false);new AlertDialog.Builder(this).setTitle(title).setView(e).setPositiveButton("OK",(d,w)->{String s=e.getText().toString().trim();if(!s.isEmpty())c.run(s);}).setNegativeButton("Annulla",null).show();}
  EditText input(String initial,boolean password){EditText e=new EditText(this);e.setSingleLine();e.setText(initial);if(password)e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);return e;}
  void showPlaylistDialog(){new AlertDialog.Builder(this).setTitle("Aggiungi playlist").setItems(new String[]{"Xtream Codes","M3U da URL","M3U da file"},(d,w)->{if(w==0)showXtreamDialog();else if(w==1)prompt("URL M3U / M3U8",prefs.getString(P_M3U_URL,""),s->{prefs.edit().putString(P_SOURCE,"m3u_url").putString(P_M3U_URL,s).apply();loadM3uUrl(s);});else pick(REQ_M3U);}).setNegativeButton("Chiudi",null).show();}
  void showXtreamDialog(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(22),dp(6),dp(22),0);EditText server=input(prefs.getString(P_X_SERVER,""),false),user=input(prefs.getString(P_X_USER,""),false),pass=input(prefs.getString(P_X_PASS,""),true);server.setHint("Server (es. http://host:porta)");user.setHint("Username");pass.setHint("Password");box.addView(server);box.addView(user);box.addView(pass);new AlertDialog.Builder(this).setTitle("Xtream Codes").setView(box).setPositiveButton("Connetti",(d,w)->{String s=server.getText().toString().trim(),u=user.getText().toString().trim(),p=pass.getText().toString();if(!s.isEmpty()&&!u.isEmpty()){prefs.edit().putString(P_SOURCE,"xtream").putString(P_X_SERVER,s).putString(P_X_USER,u).putString(P_X_PASS,p).apply();loadXtream(s,u,p);}}).setNegativeButton("Annulla",null).show();}
  void showEpgDialog(){new AlertDialog.Builder(this).setTitle("Sorgenti EPG").setItems(new String[]{"Aggiungi URL XML / GZ","Aggiungi file XML / GZ","Aggiorna EPG"},(d,w)->{if(w==0)prompt("URL EPG XMLTV","",s->{Set<String>x=new HashSet<>(prefs.getStringSet(P_EPG_URLS,new HashSet<>()));x.add(s);prefs.edit().putStringSet(P_EPG_URLS,x).apply();loadEpg();});else if(w==1)pick(REQ_EPG);else loadEpg();}).setNegativeButton("Chiudi",null).show();}
  void pick(int req){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");startActivityForResult(i,req);}
  @Override protected void onActivityResult(int r,int result,Intent data){super.onActivityResult(r,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri u=data.getData();try{getContentResolver().takePersistableUriPermission(u,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}if(r==REQ_M3U){prefs.edit().putString(P_SOURCE,"m3u_file").putString(P_M3U_URI,u.toString()).apply();loadM3uUri(u);}else if(r==REQ_EPG){Set<String>x=new HashSet<>(prefs.getStringSet(P_EPG_URIS,new HashSet<>()));x.add(u.toString());prefs.edit().putStringSet(P_EPG_URIS,x).apply();loadEpg();}}

  void restore(){String type=prefs.getString(P_SOURCE,"");if("xtream".equals(type))loadXtream(prefs.getString(P_X_SERVER,""),prefs.getString(P_X_USER,""),prefs.getString(P_X_PASS,""));else if("m3u_url".equals(type))loadM3uUrl(prefs.getString(P_M3U_URL,""));else if("m3u_file".equals(type)){String s=prefs.getString(P_M3U_URI,"");if(!s.isEmpty())loadM3uUri(Uri.parse(s));}else loadEpg();}
  void loadXtream(String server,String user,String pass){if(server.isEmpty()||user.isEmpty())return;busy("Connessione Xtream…");io.execute(()->{try{XtreamClient.Result r=XtreamClient.load(server,user,pass);List<Channel> list=new ArrayList<>();for(XtreamClient.Item x:r.items){Channel c=new Channel();c.id=x.epgId.isEmpty()?x.id:x.epgId;c.tvgName=x.name;c.name=x.name;c.group=x.category;c.logo=x.logo;c.url=x.url;list.add(c);}prefs.edit().putString(P_X_EPG,r.epgUrl).apply();applyChannels(list,"Xtream · "+server);loadEpg();}catch(Exception e){fail("Xtream: "+safe(e.getMessage()));}});}
  void loadM3uUrl(String s){if(s.isEmpty())return;busy("Carico playlist…");io.execute(()->{try{applyChannels(parseM3u(readText(openHttp(s))),"M3U · URL");loadEpg();}catch(Exception e){fail("Playlist: "+safe(e.getMessage()));}});}
  void loadM3uUri(Uri u){busy("Leggo playlist…");io.execute(()->{try(InputStream in=getContentResolver().openInputStream(u)){applyChannels(parseM3u(readText(in)),"M3U · file locale");loadEpg();}catch(Exception e){fail("Playlist: "+safe(e.getMessage()));}});}
  void applyChannels(List<Channel>x,String source){main.post(()->{channels.clear();channels.addAll(x);mode=MODE_ALL;query="";selectedCategory="Tutti";header.setText("Live TV");refreshCategories();refreshChannels();subtitle.setText(source+"  ·  "+channels.size()+" canali");idle("Playlist aggiornata");});}

  List<Channel> parseM3u(String text){List<Channel> out=new ArrayList<>();String[] lines=text.replace("\r","").split("\n");Pattern a=Pattern.compile("([A-Za-z0-9_-]+)=\"([^\"]*)\"");for(int i=0;i<lines.length;i++){String line=lines[i].trim();if(!line.startsWith("#EXTINF"))continue;String url="";for(int j=i+1;j<lines.length;j++){String n=lines[j].trim();if(n.isEmpty())continue;if(!n.startsWith("#")){url=n;i=j;}break;}if(url.isEmpty())continue;Channel c=new Channel();c.url=url;int comma=line.lastIndexOf(',');c.name=comma>=0?line.substring(comma+1).trim():"Canale";Matcher m=a.matcher(line);while(m.find()){String k=m.group(1).toLowerCase(Locale.US),v=m.group(2);if(k.equals("tvg-id"))c.id=v;else if(k.equals("tvg-name"))c.tvgName=v;else if(k.equals("group-title"))c.group=v;else if(k.equals("tvg-logo"))c.logo=v;}if(c.name.isEmpty())c.name=c.tvgName.isEmpty()?"Canale":c.tvgName;if(c.group.isEmpty())c.group="Altro";out.add(c);}return out;}

  void refreshCategories(){TreeSet<String>groups=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);for(Channel c:channels)if(matchesMode(c)&&matchesQuery(c))groups.add(c.group.isEmpty()?"Altro":c.group);categoryRows.clear();Channel all=new Channel();all.name="Tutti";categoryRows.add(all);for(String g:groups){Channel x=new Channel();x.name=g;categoryRows.add(x);}categoryAdapter.notifyDataSetChanged();}
  void refreshChannels(){shown.clear();List<String>recent=recentKeys();for(Channel c:channels){if(!matchesMode(c)||!matchesQuery(c))continue;if(!"Tutti".equals(selectedCategory)&&!selectedCategory.equals(c.group))continue;shown.add(c);}if(mode==MODE_RECENT)shown.sort(Comparator.comparingInt(c->{int i=recent.indexOf(c.key());return i<0?9999:i;}));channelAdapter.notifyDataSetChanged();subtitle.setText(shown.size()+" canali  ·  "+selectedCategory);}
  boolean matchesQuery(Channel c){String q=query.toLowerCase(Locale.US);return q.isEmpty()||c.name.toLowerCase(Locale.US).contains(q)||c.group.toLowerCase(Locale.US).contains(q);}
  boolean matchesMode(Channel c){if(mode==MODE_FAV)return favs.contains(c.key());if(mode==MODE_RECENT)return recentKeys().contains(c.key());return true;}

  void play(Channel c){title.setText(c.name+(favs.contains(c.key())?"  ★":""));channelMeta.setText((c.group.isEmpty()?"Live TV":c.group)+"  ·  MYEPG");showProgramme(c);addRecent(c);try{MediaItem item=new MediaItem.Builder().setUri(c.url).setMediaId(c.key()).build();player.setMediaItem(item);player.prepare();player.play();status.setText("Avvio stream…");}catch(Exception e){fail("Player: "+safe(e.getMessage()));}}
  void toggleFav(Channel c){if(favs.contains(c.key()))favs.remove(c.key());else favs.add(c.key());prefs.edit().putStringSet(P_FAV,new HashSet<>(favs)).apply();channelAdapter.notifyDataSetChanged();if(mode==MODE_FAV)refreshChannels();}
  void addRecent(Channel c){List<String>r=recentKeys();r.remove(c.key());r.add(0,c.key());if(r.size()>30)r=new ArrayList<>(r.subList(0,30));prefs.edit().putString(P_RECENT,join(r)).apply();}
  List<String> recentKeys(){String s=prefs.getString(P_RECENT,"");return s.isEmpty()?new ArrayList<>():new ArrayList<>(Arrays.asList(s.split("\\u001F",-1)));}
  String join(List<String>x){StringBuilder b=new StringBuilder();for(String s:x){if(b.length()>0)b.append('\u001F');b.append(s);}return b.toString();}

  void loadEpg(){busy("Aggiorno EPG…");io.execute(()->{Map<String,List<Programme>>ids=new HashMap<>(),names=new HashMap<>();int ok=0;Set<String>urls=new HashSet<>(prefs.getStringSet(P_EPG_URLS,new HashSet<>()));String xe=prefs.getString(P_X_EPG,"");if(!xe.isEmpty())urls.add(xe);for(String s:urls)try(InputStream in=maybeGzip(openHttp(s),s)){parseXml(in,ids,names);ok++;}catch(Exception ignored){}for(String s:prefs.getStringSet(P_EPG_URIS,new HashSet<>()))try(InputStream raw=getContentResolver().openInputStream(Uri.parse(s));InputStream in=maybeGzip(raw,s)){parseXml(in,ids,names);ok++;}catch(Exception ignored){}final int count=ok;main.post(()->{epgId.clear();epgId.putAll(ids);epgName.clear();epgName.putAll(names);spinner.setVisibility(View.GONE);status.setText(count==0?"Nessuna sorgente EPG caricata":count+" sorgenti EPG aggiornate");});});}
  void parseXml(InputStream in,Map<String,List<Programme>>ids,Map<String,List<Programme>>names)throws Exception{XmlPullParser p=XmlPullParserFactory.newInstance().newPullParser();p.setInput(in,"UTF-8");Map<String,String>channelNames=new HashMap<>();String cid=null;for(int e=p.getEventType();e!=XmlPullParser.END_DOCUMENT;e=p.next()){if(e==XmlPullParser.START_TAG&&"channel".equals(p.getName()))cid=safe(p.getAttributeValue(null,"id"));else if(e==XmlPullParser.START_TAG&&"display-name".equals(p.getName())&&cid!=null){String n=p.nextText().trim();if(!n.isEmpty())channelNames.put(cid,n);}else if(e==XmlPullParser.END_TAG&&"channel".equals(p.getName()))cid=null;else if(e==XmlPullParser.START_TAG&&"programme".equals(p.getName())){Programme pr=new Programme();pr.channel=safe(p.getAttributeValue(null,"channel"));pr.start=date(p.getAttributeValue(null,"start"));pr.stop=date(p.getAttributeValue(null,"stop"));int depth=1;while(depth>0){int x=p.next();if(x==XmlPullParser.START_TAG){depth++;if("title".equals(p.getName())){pr.title=p.nextText();depth--;}}else if(x==XmlPullParser.END_TAG)depth--;}if(!pr.channel.isEmpty()&&pr.start>0)ids.computeIfAbsent(pr.channel,k->new ArrayList<>()).add(pr);}}for(Map.Entry<String,String>e:channelNames.entrySet()){List<Programme>l=ids.get(e.getKey());if(l!=null)names.put(norm(e.getValue()),l);}for(List<Programme>l:ids.values())Collections.sort(l,Comparator.comparingLong(a->a.start));}
  void showProgramme(Channel c){List<Programme>l=!c.id.isEmpty()?epgId.get(c.id):null;if(l==null)l=epgName.get(norm(c.tvgName.isEmpty()?c.name:c.tvgName));long n=System.currentTimeMillis();Programme cur=null,nxt=null;if(l!=null)for(Programme p:l){if(p.start<=n&&(p.stop==0||p.stop>n))cur=p;else if(p.start>n){nxt=p;break;}}now.setText("ORA   "+fmt(cur));next.setText("DOPO  "+fmt(nxt));}
  String fmt(Programme p){if(p==null)return"—";SimpleDateFormat f=new SimpleDateFormat("HH:mm",Locale.getDefault());return f.format(new Date(p.start))+(p.stop>0?"–"+f.format(new Date(p.stop)):"")+"   "+(p.title.isEmpty()?"Senza titolo":p.title);}

  long date(String raw){if(raw==null)return 0;String[]p=raw.trim().split("\\s+");String stamp=p[0],zone=p.length>1?p[1]:"+0000";try{return new SimpleDateFormat("yyyyMMddHHmmss Z",Locale.US).parse(stamp+" "+zone).getTime();}catch(Exception e){return 0;}}
  InputStream openHttp(String s)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(s).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(30000);c.setInstanceFollowRedirects(true);c.setRequestProperty("User-Agent","MYEPG Player/0.3");int code=c.getResponseCode();if(code<200||code>=300)throw new IOException("HTTP "+code);return c.getInputStream();}
  InputStream maybeGzip(InputStream raw,String name)throws Exception{BufferedInputStream b=new BufferedInputStream(raw);b.mark(4);int a=b.read(),c=b.read();b.reset();return name.toLowerCase(Locale.US).endsWith(".gz")||(a==0x1f&&c==0x8b)?new GZIPInputStream(b):b;}
  String readText(InputStream in)throws Exception{try(BufferedReader r=new BufferedReader(new InputStreamReader(in))){StringBuilder b=new StringBuilder();String s;while((s=r.readLine())!=null)b.append(s).append('\n');return b.toString();}}
  void busy(String s){main.post(()->{spinner.setVisibility(View.VISIBLE);status.setText(s);});}void idle(String s){spinner.setVisibility(View.GONE);status.setText(s);}void fail(String s){main.post(()->{spinner.setVisibility(View.GONE);status.setText(s);Toast.makeText(this,s,Toast.LENGTH_LONG).show();});}
  static String safe(String s){return s==null?"":s;}static String norm(String s){return safe(s).toLowerCase(Locale.US).replaceAll("[^a-z0-9]+","");}
  @Override public boolean dispatchKeyEvent(KeyEvent e){if(e.getAction()==KeyEvent.ACTION_DOWN&&e.getKeyCode()==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE&&player!=null){if(player.isPlaying())player.pause();else player.play();return true;}return super.dispatchKeyEvent(e);}
  @Override protected void onDestroy(){super.onDestroy();io.shutdownNow();if(player!=null)player.release();}
  static class Channel{String id="",tvgName="",name="",group="",logo="",url="";String key(){return!url.isEmpty()?url:(!id.isEmpty()?id:name);}}
  static class Programme{String channel="",title="";long start,stop;}
}
