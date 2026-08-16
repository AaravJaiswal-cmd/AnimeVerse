import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Main {
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "8000"));
    private static final String KITSU = "https://kitsu.io/api/edge";
    private static final Path PUBLIC_DIR = Path.of("public").toAbsolutePath().normalize();
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private static final Map<String,String> CATEGORY_CACHE = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        if (!Files.exists(PUBLIC_DIR.resolve("index.html"))) throw new IOException("public/index.html not found");
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", PORT), 0);
        server.createContext("/api/anime", Main::anime);
        server.createContext("/api/quiz", Main::quiz);
        server.createContext("/api/random", Main::randomAnime);
        server.createContext("/api/health", Main::health);
        server.createContext("/", Main::staticFiles);
        server.start();
        System.out.println("AnimeVerse running on http://localhost:" + PORT);
    }

    private static void anime(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { send(ex,405,"Method Not Allowed","text/plain"); return; }
        Map<String,String> p = params(ex.getRequestURI().getRawQuery());
        int page = Math.max(1, integer(p.get("page"),1));
        int limit = 20;
        String search = p.getOrDefault("search", "").trim();
        String sort = p.getOrDefault("sort", "popularity");
        String status = p.getOrDefault("status", "");
        String genre = p.getOrDefault("genre", "").trim();
        StringBuilder url = new StringBuilder(KITSU + "/anime?page%5Blimit%5D=" + limit + "&page%5Boffset%5D=" + ((page-1)*limit));
        if (!search.isEmpty()) url.append("&filter%5Btext%5D=").append(enc(search));
        if ("current".equals(status) || "finished".equals(status)) url.append("&filter%5Bstatus%5D=").append(status);
        if (!genre.isEmpty()) {
            String slug = quizSlug(genre);
            if (slug.isEmpty()) {
                send(ex, 400, error("Unknown anime category: " + genre), "application/json; charset=UTF-8");
                return;
            }
            url.append("&filter%5Bcategories%5D=").append(enc(slug));
        }
        if ("score".equals(sort)) url.append("&sort=-averageRating");
        else if ("newest".equals(sort)) url.append("&sort=-startDate");
        else if ("trending".equals(sort)) url.append("&sort=-popularityRank");
        else url.append("&sort=popularityRank");
        try {
            String raw = get(url.toString());
            send(ex,200,convertList(raw,page,limit),"application/json; charset=UTF-8");
        } catch(Exception e) { send(ex,502,error(e.getMessage()),"application/json; charset=UTF-8"); }
    }

    private static void randomAnime(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            send(ex, 405, "Method Not Allowed", "text/plain");
            return;
        }
        Map<String,String> p = params(ex.getRequestURI().getRawQuery());
        String genre = p.getOrDefault("genre", "").trim();
        String slug = genre.isEmpty() ? "" : quizSlug(genre);
        try {
            StringBuilder url = new StringBuilder(KITSU + "/anime?page%5Blimit%5D=20&sort=-popularityRank");
            if (!slug.isEmpty()) url.append("&filter%5Bcategories%5D=").append(enc(slug));
            String raw = get(url.toString());
            List<String> objects = extractObjects(raw);
            if (objects.isEmpty()) throw new IOException("No anime found.");
            String anime = convertAnime(objects.get((int)(Math.random() * objects.size())));
            send(ex, 200, "{\"anime\":" + anime + "}", "application/json; charset=UTF-8");
        } catch (Exception e) {
            send(ex, 502, error(e.getMessage()), "application/json; charset=UTF-8");
        }
    }

    private static void quiz(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { send(ex,405,"Method Not Allowed","text/plain"); return; }
        Map<String,String> p = params(ex.getRequestURI().getRawQuery());
        String taste = p.getOrDefault("taste","action");
        String slug = quizSlug(taste);
        try {
            if (slug.isEmpty()) throw new IOException("Unknown anime category: " + slug);
            String raw = get(KITSU + "/anime?filter%5Bcategories%5D=" + enc(slug) + "&page%5Blimit%5D=20&sort=popularityRank");
            List<String> objects = extractObjects(raw);
            if (objects.isEmpty()) throw new IOException("No anime found for taste: " + taste);
            int index = (int)(Math.random() * Math.min(objects.size(), 10));
            String anime = convertAnime(objects.get(index));
            String genre = quizLabel(taste);
            send(ex,200,"{\"taste\":\""+esc(genre)+"\",\"anime\":"+anime+"}","application/json; charset=UTF-8");
        } catch(Exception e) { send(ex,502,error(e.getMessage()),"application/json; charset=UTF-8"); }
    }

    private static String quizSlug(String taste) {
        return switch(taste.toLowerCase()) {
            case "mystery" -> "mystery";
            case "comedy" -> "comedy";
            case "romance" -> "romance";
            case "fantasy" -> "fantasy";
            case "scifi" -> "science-fiction";
            case "psychological" -> "psychological";
            case "slice" -> "slice-of-life";
            case "sports" -> "sports";
            case "seinen" -> "seinen";
            case "shoujo" -> "shoujo";
            case "josei" -> "josei";
            case "shounen" -> "shounen";
            default -> "action";
        };
    }

    private static String quizLabel(String taste) {
        return switch(taste.toLowerCase()) {
            case "mystery" -> "Mystery"; case "comedy" -> "Comedy"; case "romance" -> "Romance";
            case "fantasy" -> "Fantasy"; case "scifi" -> "Sci-Fi"; case "psychological" -> "Psychological";
            case "slice" -> "Slice of Life"; case "sports" -> "Sports"; case "seinen" -> "Seinen";
            case "shoujo" -> "Shōjo"; case "josei" -> "Josei"; case "shounen" -> "Shōnen";
            default -> "Action";
        };
    }

    private static void health(HttpExchange ex) throws IOException {
        send(ex,200,"{\"status\":\"online\",\"source\":\"Kitsu\",\"quiz\":true}","application/json; charset=UTF-8");
    }

    private static String convertList(String raw,int page,int limit) {
        List<String> a = extractObjects(raw);
        int total = numberAfter(raw,"\"count\"");
        int last = total > 0 ? (int)Math.ceil(total/(double)limit) : page + (a.size()==limit?1:0);
        StringBuilder b=new StringBuilder("{\"pagination\":{\"current_page\":"+page+",\"last_page\":"+last+",\"has_next_page\":"+(page<last)+"},\"data\":[");
        for(int i=0;i<a.size();i++){if(i>0)b.append(',');b.append(convertAnime(a.get(i)));} return b.append("]}").toString();
    }

    private static String convertAnime(String o) {
        String title = getString(o,"canonicalTitle");
        String synopsis = getString(o,"synopsis");
        String rating = getString(o,"averageRating");
        String episodes = numberString(o,"episodeCount");
        String status = getString(o,"status");
        String start = getString(o,"startDate");
        String year = start.length()>=4?start.substring(0,4):"";
        String image = image(o);
        String en = titleByLang(o,"en"); if(en.isEmpty()) en=title;
        String ja = titleByLang(o,"ja_jp"); if(ja.isEmpty()) ja=title;
        return "{\"title\":\""+esc(en)+"\",\"title_english\":\""+esc(en)+"\",\"title_japanese\":\""+esc(ja)+"\",\"synopsis\":\""+esc(synopsis.isEmpty()?"No description available.":synopsis)+"\",\"score\":"+num(rating)+",\"episodes\":"+num(episodes)+",\"year\":"+num(year)+",\"status\":\""+esc(status)+"\",\"images\":{\"jpg\":{\"image_url\":\""+esc(image)+"\",\"large_image_url\":\""+esc(image)+"\"}},\"genres\":[]}";
    }

    private static String get(String url) throws Exception {
        HttpRequest r=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).header("Accept","application/vnd.api+json").header("User-Agent","AnimeVerse/2.0").GET().build();
        HttpResponse<String> s=CLIENT.send(r,HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if(s.statusCode()!=200) throw new IOException("Kitsu HTTP "+s.statusCode()); return s.body();
    }

    private static List<String> extractObjects(String json) {
        List<String> out=new ArrayList<>(); int dp=json.indexOf("\"data\""); if(dp<0)return out; int start=json.indexOf('[',dp); if(start<0)return out;
        int depth=0,obj=-1; boolean str=false,esc=false;
        for(int i=start+1;i<json.length();i++){char c=json.charAt(i); if(esc){esc=false;continue;} if(c=='\\'&&str){esc=true;continue;} if(c=='\"'){str=!str;continue;} if(str)continue; if(c=='{'){if(depth==0)obj=i;depth++;} else if(c=='}'){depth--;if(depth==0&&obj>=0){out.add(json.substring(obj,i+1));obj=-1;}} else if(c==']'&&depth==0)break;} return out;
    }

    private static String categoryIdForSlug(String slug) throws Exception {
        if (slug == null || slug.isEmpty()) return "";
        String cached = CATEGORY_CACHE.get(slug);
        if (cached != null) return cached;

        // Resolve against Kitsu's category records instead of trusting the UI label.
        // We scan pages so this still works if Kitsu caps the page size.
        final int limit = 20;
        for (int offset = 0; offset < 400; offset += limit) {
            String raw = get(KITSU + "/categories?page%5Blimit%5D=" + limit + "&page%5Boffset%5D=" + offset);
            List<String> objects = extractObjects(raw);
            if (objects.isEmpty()) break;
            for (String object : objects) {
                String candidateSlug = getNestedAttribute(object, "slug");
                if (slug.equalsIgnoreCase(candidateSlug)) {
                    String id = getString(object, "id");
                    if (!id.isEmpty()) CATEGORY_CACHE.put(slug, id);
                    return id;
                }
            }
            if (objects.size() < limit) break;
        }
        return "";
    }

    private static String getNestedAttribute(String object, String key) {
        int attributes = object.indexOf("\"attributes\"");
        if (attributes < 0) return "";
        return getString(object.substring(attributes), key);
    }

    private static String firstId(String json) { List<String> o=extractObjects(json); return o.isEmpty()?"":getString(o.get(0),"id"); }

    private static String getString(String o,String key){String k="\""+key+"\"";int p=o.indexOf(k);if(p<0)return"";int c=o.indexOf(':',p+k.length());if(c<0)return"";int q=o.indexOf('"',c+1);if(q<0)return"";int e=closeQuote(o,q+1);return e<0?"":unesc(o.substring(q+1,e));}
    private static String titleByLang(String o,String lang){int p=o.indexOf("\"titles\"");if(p<0)return"";return getString(o.substring(p),lang);}
    private static String image(String o){int p=o.indexOf("\"posterImage\"");if(p<0)return"";for(String s:new String[]{"large","medium","small","tiny"}){String v=getString(o.substring(p),s);if(!v.isEmpty())return v;}return"";}
    private static int closeQuote(String s,int start){boolean e=false;for(int i=start;i<s.length();i++){char c=s.charAt(i);if(e){e=false;continue;}if(c=='\\'){e=true;continue;}if(c=='"')return i;}return-1;}
    private static int numberAfter(String s,String key){int p=s.indexOf(key);if(p<0)return 0;int c=s.indexOf(':',p);if(c<0)return 0;int i=c+1;while(i<s.length()&&Character.isWhitespace(s.charAt(i)))i++;int j=i;while(j<s.length()&&Character.isDigit(s.charAt(j)))j++;try{return Integer.parseInt(s.substring(i,j));}catch(Exception e){return 0;}}
    private static String numberString(String o,String key){
        String k="\"" + key + "\"";
        int p=o.indexOf(k);
        if(p<0)return"";
        int c=o.indexOf(':',p+k.length());
        if(c<0)return"";
        int i=c+1;
        while(i<o.length() && Character.isWhitespace(o.charAt(i)))i++;
        int j=i;
        while(j<o.length() && (Character.isDigit(o.charAt(j)) || o.charAt(j)=='.' || o.charAt(j)=='-'))j++;
        return j==i?"":o.substring(i,j);
    }
    private static String num(String v){if(v==null||v.isEmpty())return"null";try{Double.parseDouble(v);return v;}catch(Exception e){return"null";}}
    private static String esc(String s){return s==null?"":s.replace("\\","\\\\").replace("\"","\\\"").replace("\r","\\r").replace("\n","\\n");}
    private static String unesc(String s){return s.replace("\\\"","\"").replace("\\n","\n").replace("\\r","\r").replace("\\\\","\\");}
    private static String enc(String s){return URLEncoder.encode(s,StandardCharsets.UTF_8);}
    private static int integer(String s,int d){try{return Integer.parseInt(s);}catch(Exception e){return d;}}
    private static Map<String,String> params(String q){Map<String,String> m=new HashMap<>();if(q==null||q.isEmpty())return m;for(String part:q.split("&")){String[] x=part.split("=",2);String k=URLDecoder.decode(x[0],StandardCharsets.UTF_8);String v=x.length>1?URLDecoder.decode(x[1],StandardCharsets.UTF_8):"";m.put(k,v);}return m;}
    private static String error(String s){return "{\"error\":\""+esc(s==null?"Unknown error":s)+"\"}";}

    private static void staticFiles(HttpExchange ex) throws IOException {
        String p=ex.getRequestURI().getPath(); if(p.equals("/"))p="/index.html"; Path f=PUBLIC_DIR.resolve(p.substring(1)).normalize();
        if(!f.startsWith(PUBLIC_DIR)||!Files.exists(f)||Files.isDirectory(f)){send(ex,404,"404 Not Found","text/plain");return;}
        byte[] data=Files.readAllBytes(f);String t=type(p);ex.getResponseHeaders().set("Content-Type",t);ex.sendResponseHeaders(200,data.length);try(OutputStream o=ex.getResponseBody()){o.write(data);}
    }
    private static String type(String p){if(p.endsWith(".css"))return"text/css; charset=UTF-8";if(p.endsWith(".js"))return"application/javascript; charset=UTF-8";if(p.endsWith(".png"))return"image/png";if(p.endsWith(".jpg")||p.endsWith(".jpeg")||p.endsWith(".webp"))return"image/jpeg";return"text/html; charset=UTF-8";}
    private static void send(HttpExchange ex,int code,String body,String type)throws IOException{byte[] d=body.getBytes(StandardCharsets.UTF_8);ex.getResponseHeaders().set("Content-Type",type);ex.getResponseHeaders().set("Cache-Control","no-store");ex.sendResponseHeaders(code,d.length);try(OutputStream o=ex.getResponseBody()){o.write(d);}}
}
