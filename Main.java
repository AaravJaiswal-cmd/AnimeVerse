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


public class Main {

    private static final int PORT =
    Integer.parseInt(
        System.getenv().getOrDefault("PORT", "8000")
    );

    private static final String KITSU_API =
        "https://kitsu.io/api/edge/anime";

    private static final Path PUBLIC_DIR =
        Path.of("public")
            .toAbsolutePath()
            .normalize();

    private static final HttpClient CLIENT =
        HttpClient.newBuilder()
            .connectTimeout(
                Duration.ofSeconds(15)
            )
            .build();


    // =====================================================
    // MAIN
    // =====================================================

    public static void main(String[] args)
        throws Exception {

        if (!Files.exists(
            PUBLIC_DIR.resolve("index.html"))) {

            throw new Exception(
                "public/index.html was not found."
            );
        }


        HttpServer server =
            HttpServer.create(
                new InetSocketAddress(
                    "0.0.0.0",
                    PORT
                ),
                0
            );


        server.createContext(
            "/api/anime",
            Main::handleAnime
        );


        server.createContext(
            "/api/random",
            Main::handleRandom
        );


        server.createContext(
            "/api/health",
            Main::handleHealth
        );


        server.createContext(
            "/",
            Main::handleWebsite
        );


        server.start();


        System.out.println();
        System.out.println(
            "======================================"
        );
        System.out.println(
            "          ANIMEVERSE SERVER"
        );
        System.out.println(
            "======================================"
        );

        System.out.println();

        System.out.println(
            "Website:"
        );

        System.out.println(
            "http://localhost:8000"
        );

        System.out.println();

        System.out.println(
            "Live API: Kitsu"
        );

        System.out.println();

        System.out.println(
            "Server is running..."
        );
    }


    // =====================================================
    // ANIME SEARCH
    // =====================================================

    private static void handleAnime(
        HttpExchange exchange
    ) throws IOException {

        if (!exchange.getRequestMethod()
                .equalsIgnoreCase("GET")) {

            send(
                exchange,
                405,
                "Method Not Allowed",
                "text/plain"
            );

            return;
        }


        Map<String, String> params =
            parseParameters(
                exchange
                    .getRequestURI()
                    .getRawQuery()
            );


        int page =
            parseInt(
                params.get("page"),
                1
            );


        if (page < 1) {
            page = 1;
        }


        String search =
            params.getOrDefault(
                "search",
                ""
            );


        String status =
            params.getOrDefault(
                "status",
                ""
            );


        String sort =
            params.getOrDefault(
                "sort",
                "popularity"
            );


        int limit = 20;


        int offset =
            (page - 1) * limit;


        StringBuilder url =
            new StringBuilder();


        url.append(KITSU_API);

        url.append("?");

        url.append(
            "page%5Blimit%5D="
        );

        url.append(limit);

        url.append(
            "&page%5Boffset%5D="
        );

        url.append(offset);


        // -------------------------------------------------
        // SEARCH
        // -------------------------------------------------

        if (!search.isEmpty()) {

            url.append(
                "&filter%5Btext%5D="
            );

            url.append(
                encode(search)
            );
        }


        // -------------------------------------------------
        // STATUS
        // -------------------------------------------------

        if (!status.isEmpty()) {

            String kitsuStatus =
                convertStatus(status);


            if (!kitsuStatus.isEmpty()) {

                url.append(
                    "&filter%5Bstatus%5D="
                );

                url.append(
                    encode(kitsuStatus)
                );
            }
        }


        // -------------------------------------------------
        // SORT
        // -------------------------------------------------

        if (sort.equalsIgnoreCase("score")) {

            url.append(
                "&sort=-averageRating"
            );

        } else if (
            sort.equalsIgnoreCase("newest")
        ) {

            url.append(
                "&sort=-startDate"
            );

        } else if (
            sort.equalsIgnoreCase("trending")
        ) {

            url.append(
                "&sort=-popularityRank"
            );

        } else {

            url.append(
                "&sort=popularityRank"
            );
        }


        System.out.println();

        System.out.println(
            "Requesting live anime data..."
        );

        System.out.println(
            url.toString()
        );


        try {

            String kitsuResponse =
                request(url.toString());


            String converted =
                convertResponse(
                    kitsuResponse,
                    page,
                    limit
                );


            send(
                exchange,
                200,
                converted,
                "application/json; charset=UTF-8"
            );


        } catch (Exception e) {

            System.out.println(
                "API ERROR: "
                + e.getMessage()
            );


            String error =
                "{"
                + "\"error\":\""
                + escape(
                    e.getMessage()
                )
                + "\""
                + "}";


            send(
                exchange,
                502,
                error,
                "application/json; charset=UTF-8"
            );
        }
    }


    // =====================================================
    // RANDOM ANIME
    // =====================================================

    private static void handleRandom(
        HttpExchange exchange
    ) throws IOException {

        try {

            int randomOffset =
                (int)(
                    Math.random() * 5000
                );


            String url =
                KITSU_API
                + "?page%5Blimit%5D=20"
                + "&page%5Boffset%5D="
                + randomOffset;


            String response =
                request(url);


            List<String> objects =
                extractAnimeObjects(
                    response
                );


            if (objects.isEmpty()) {

                throw new Exception(
                    "Kitsu returned no anime."
                );
            }


            int index =
                (int)(
                    Math.random()
                    * objects.size()
                );


            String anime =
                convertAnime(
                    objects.get(index)
                );


            String result =
                "{"
                + "\"data\":"
                + anime
                + "}";


            send(
                exchange,
                200,
                result,
                "application/json; charset=UTF-8"
            );


        } catch (Exception e) {

            String error =
                "{"
                + "\"error\":\""
                + escape(
                    e.getMessage()
                )
                + "\""
                + "}";


            send(
                exchange,
                502,
                error,
                "application/json; charset=UTF-8"
            );
        }
    }


    // =====================================================
    // HEALTH CHECK
    // =====================================================

    private static void handleHealth(
        HttpExchange exchange
    ) throws IOException {

        String result =
            "{"
            + "\"status\":\"online\","
            + "\"live\":true,"
            + "\"source\":\"Kitsu\""
            + "}";


        send(
            exchange,
            200,
            result,
            "application/json; charset=UTF-8"
        );
    }


    // =====================================================
    // CALL KITSU
    // =====================================================

    private static String request(
        String url
    ) throws Exception {

        HttpRequest request =
            HttpRequest.newBuilder()
                .uri(
                    URI.create(url)
                )
                .timeout(
                    Duration.ofSeconds(30)
                )
                .header(
                    "Accept",
                    "application/vnd.api+json"
                )
                .header(
                    "User-Agent",
                    "AnimeVerse/1.0"
                )
                .GET()
                .build();


        HttpResponse<String> response =
            CLIENT.send(
                request,
                HttpResponse.BodyHandlers
                    .ofString(
                        StandardCharsets.UTF_8
                    )
            );


        int code =
            response.statusCode();


        System.out.println(
            "Kitsu HTTP status: "
            + code
        );


        if (code != 200) {

            throw new Exception(
                "Kitsu returned HTTP "
                + code
            );
        }


        return response.body();
    }


    // =====================================================
    // CONVERT KITSU RESPONSE
    // =====================================================

    private static String convertResponse(
        String json,
        int page,
        int limit
    ) {

        List<String> objects =
            extractAnimeObjects(
                json
            );


        int total =
            extractTotal(
                json
            );


        boolean hasNext =
            objects.size() == limit;


        if (total > 0) {

            hasNext =
                page * limit < total;
        }


        StringBuilder result =
            new StringBuilder();


        result.append(
            "{"
        );


        result.append(
            "\"pagination\":{"
        );


        result.append(
            "\"current_page\":"
        );

        result.append(page);

        result.append(",");


        result.append(
            "\"has_next_page\":"
        );

        result.append(
            hasNext
        );


        result.append(
            ",\"last_page\":"
        );


        int lastPage = 1;


        if (total > 0) {

            lastPage =
                (int)Math.ceil(
                    (double)total
                    / limit
                );
        }


        result.append(
            lastPage
        );


        result.append(
            "},"
        );


        result.append(
            "\"data\":["
        );


        for (
            int i = 0;
            i < objects.size();
            i++
        ) {

            if (i > 0) {

                result.append(",");
            }


            result.append(
                convertAnime(
                    objects.get(i)
                )
            );
        }


        result.append(
            "]"
        );


        result.append(
            "}"
        );


        return result.toString();
    }


    // =====================================================
    // CONVERT ONE ANIME
    // =====================================================

    private static String convertAnime(
        String object
    ) {

        String title =
            getString(
                object,
                "canonicalTitle"
            );


        String englishTitle =
            getNestedLanguageTitle(
                object,
                "en"
            );


        if (englishTitle.isEmpty()) {

            englishTitle = title;
        }


        String japaneseTitle =
            getNestedLanguageTitle(
                object,
                "ja_jp"
            );


        if (japaneseTitle.isEmpty()) {

            japaneseTitle = title;
        }


        String synopsis =
            getString(
                object,
                "synopsis"
            );


        String rating =
            getString(
                object,
                "averageRating"
            );


        String episodes =
            getString(
                object,
                "episodeCount"
            );


        String status =
            getString(
                object,
                "status"
            );


        String startDate =
            getString(
                object,
                "startDate"
            );


        String year = "";


        if (
            startDate != null
            &&
            startDate.length() >= 4
        ) {

            year =
                startDate.substring(
                    0,
                    4
                );
        }


        String image =
            getNestedImage(
                object
            );


        if (synopsis.isEmpty()) {

            synopsis =
                "No description available.";
        }


        if (rating.isEmpty()) {

            rating = "null";
        }


        if (episodes.isEmpty()) {

            episodes = "null";
        }


        if (year.isEmpty()) {

            year = "null";
        }


        return
            "{"

            + "\"title\":\""
            + escape(
                englishTitle
            )
            + "\","

            + "\"title_english\":\""
            + escape(
                englishTitle
            )
            + "\","

            + "\"title_japanese\":\""
            + escape(
                japaneseTitle
            )
            + "\","

            + "\"synopsis\":\""
            + escape(
                synopsis
            )
            + "\","

            + "\"score\":"
            + numeric(
                rating
            )
            + ","

            + "\"episodes\":"
            + numeric(
                episodes
            )
            + ","

            + "\"year\":"
            + numeric(
                year
            )
            + ","

            + "\"status\":\""
            + escape(
                status
            )
            + "\","

            + "\"images\":{"

            + "\"jpg\":{"

            + "\"image_url\":\""
            + escape(
                image
            )
            + "\","

            + "\"large_image_url\":\""
            + escape(
                image
            )
            + "\""

            + "}"

            + "},"

            + "\"genres\":[]"

            + "}";
    }


    // =====================================================
    // EXTRACT ANIME OBJECTS
    // =====================================================

    private static List<String>
        extractAnimeObjects(
            String json
        ) {

        List<String> result =
            new ArrayList<String>();


        int dataPosition =
            json.indexOf(
                "\"data\""
            );


        if (dataPosition == -1) {

            return result;
        }


        int arrayStart =
            json.indexOf(
                "[",
                dataPosition
            );


        if (arrayStart == -1) {

            return result;
        }


        int depth = 0;

        int objectStart = -1;

        boolean inString = false;

        boolean escaped = false;


        for (
            int i = arrayStart + 1;
            i < json.length();
            i++
        ) {

            char c =
                json.charAt(i);


            if (escaped) {

                escaped = false;

                continue;
            }


            if (
                c == '\\'
                &&
                inString
            ) {

                escaped = true;

                continue;
            }


            if (c == '"') {

                inString =
                    !inString;

                continue;
            }


            if (inString) {

                continue;
            }


            if (c == '{') {

                if (depth == 0) {

                    objectStart = i;
                }

                depth++;
            }


            else if (c == '}') {

                depth--;


                if (
                    depth == 0
                    &&
                    objectStart != -1
                ) {

                    result.add(
                        json.substring(
                            objectStart,
                            i + 1
                        )
                    );


                    objectStart = -1;
                }
            }


            else if (
                c == ']'
                &&
                depth == 0
            ) {

                break;
            }
        }


        return result;
    }


    // =====================================================
    // GET STRING PROPERTY
    // =====================================================

    private static String getString(
        String object,
        String property
    ) {

        String key =
            "\""
            + property
            + "\"";


        int position =
            object.indexOf(
                key
            );


        if (position == -1) {

            return "";
        }


        int colon =
            object.indexOf(
                ":",
                position
            );


        if (colon == -1) {

            return "";
        }


        int firstQuote =
            object.indexOf(
                "\"",
                colon + 1
            );


        if (firstQuote == -1) {

            return "";
        }


        int secondQuote =
            findClosingQuote(
                object,
                firstQuote + 1
            );


        if (secondQuote == -1) {

            return "";
        }


        return unescape(
            object.substring(
                firstQuote + 1,
                secondQuote
            )
        );
    }


    // =====================================================
    // LANGUAGE TITLE
    // =====================================================

    private static String
        getNestedLanguageTitle(
            String object,
            String language
        ) {

        String titlesKey =
            "\"titles\"";


        int titlesPosition =
            object.indexOf(
                titlesKey
            );


        if (titlesPosition == -1) {

            return "";
        }


        String languageKey =
            "\""
            + language
            + "\"";


        int languagePosition =
            object.indexOf(
                languageKey,
                titlesPosition
            );


        if (languagePosition == -1) {

            return "";
        }


        int colon =
            object.indexOf(
                ":",
                languagePosition
            );


        if (colon == -1) {

            return "";
        }


        int firstQuote =
            object.indexOf(
                "\"",
                colon + 1
            );


        if (firstQuote == -1) {

            return "";
        }


        int secondQuote =
            findClosingQuote(
                object,
                firstQuote + 1
            );


        if (secondQuote == -1) {

            return "";
        }


        return unescape(
            object.substring(
                firstQuote + 1,
                secondQuote
            )
        );
    }


    // =====================================================
    // IMAGE
    // =====================================================

    private static String getNestedImage(
        String object
    ) {

        int imagePosition =
            object.indexOf(
                "\"posterImage\""
            );


        if (imagePosition == -1) {

            return "";
        }


        String[] possible =
            {
                "large",
                "medium",
                "small",
                "tiny"
            };


        for (
            String size : possible
        ) {

            String key =
                "\""
                + size
                + "\"";


            int position =
                object.indexOf(
                    key,
                    imagePosition
                );


            if (position == -1) {

                continue;
            }


            int colon =
                object.indexOf(
                    ":",
                    position
                );


            if (colon == -1) {

                continue;
            }


            int quote =
                object.indexOf(
                    "\"",
                    colon + 1
                );


            if (quote == -1) {

                continue;
            }


            int end =
                findClosingQuote(
                    object,
                    quote + 1
                );


            if (end == -1) {

                continue;
            }


            return unescape(
                object.substring(
                    quote + 1,
                    end
                )
            );
        }


        return "";
    }


    // =====================================================
    // FIND CLOSING QUOTE
    // =====================================================

    private static int findClosingQuote(
        String text,
        int start
    ) {

        boolean escaped = false;


        for (
            int i = start;
            i < text.length();
            i++
        ) {

            char c =
                text.charAt(i);


            if (escaped) {

                escaped = false;

                continue;
            }


            if (c == '\\') {

                escaped = true;

                continue;
            }


            if (c == '"') {

                return i;
            }
        }


        return -1;
    }


    // =====================================================
    // TOTAL RESULTS
    // =====================================================

    private static int extractTotal(
        String json
    ) {

        int meta =
            json.indexOf(
                "\"meta\""
            );


        if (meta == -1) {

            return 0;
        }


        int count =
            json.indexOf(
                "\"count\"",
                meta
            );


        if (count == -1) {

            return 0;
        }


        int colon =
            json.indexOf(
                ":",
                count
            );


        if (colon == -1) {

            return 0;
        }


        int start =
            colon + 1;


        while (
            start < json.length()
            &&
            Character.isWhitespace(
                json.charAt(start)
            )
        ) {

            start++;
        }


        int end = start;


        while (
            end < json.length()
            &&
            Character.isDigit(
                json.charAt(end)
            )
        ) {

            end++;
        }


        try {

            return Integer.parseInt(
                json.substring(
                    start,
                    end
                )
            );

        } catch (Exception e) {

            return 0;
        }
    }


    // =====================================================
    // PARAMETERS
    // =====================================================

    private static Map<String, String>
        parseParameters(
            String query
        ) {

        Map<String, String> result =
            new HashMap<String, String>();


        if (
            query == null
            ||
            query.isEmpty()
        ) {

            return result;
        }


        String[] parts =
            query.split(
                "&"
            );


        for (
            String part : parts
        ) {

            String[] pair =
                part.split(
                    "=",
                    2
                );


            String key =
                URLDecoder.decode(
                    pair[0],
                    StandardCharsets.UTF_8
                );


            String value = "";


            if (pair.length > 1) {

                value =
                    URLDecoder.decode(
                        pair[1],
                        StandardCharsets.UTF_8
                    );
            }


            result.put(
                key,
                value
            );
        }


        return result;
    }


    // =====================================================
    // STATUS CONVERSION
    // =====================================================

    private static String convertStatus(
        String status
    ) {

        if (
            status.equalsIgnoreCase(
                "Currently Airing"
            )
        ) {

            return "current";
        }


        if (
            status.equalsIgnoreCase(
                "Finished"
            )
        ) {

            return "finished";
        }


        return "";
    }


    // =====================================================
    // ENCODE
    // =====================================================

    private static String encode(
        String text
    ) {

        return URLEncoder.encode(
            text,
            StandardCharsets.UTF_8
        );
    }


    // =====================================================
    // NUMERIC
    // =====================================================

    private static String numeric(
        String value
    ) {

        if (
            value == null
            ||
            value.isEmpty()
        ) {

            return "null";
        }


        try {

            Double.parseDouble(
                value
            );


            return value;

        } catch (Exception e) {

            return "null";
        }
    }


    // =====================================================
    // ESCAPE
    // =====================================================

    private static String escape(
        String text
    ) {

        if (text == null) {

            return "";
        }


        return text
            .replace(
                "\\",
                "\\\\"
            )
            .replace(
                "\"",
                "\\\""
            )
            .replace(
                "\r",
                "\\r"
            )
            .replace(
                "\n",
                "\\n"
            );
    }


    // =====================================================
    // UNESCAPE
    // =====================================================

    private static String unescape(
        String text
    ) {

        if (text == null) {

            return "";
        }


        return text
            .replace(
                "\\\"",
                "\""
            )
            .replace(
                "\\n",
                "\n"
            )
            .replace(
                "\\r",
                "\r"
            )
            .replace(
                "\\\\",
                "\\"
            );
    }


    // =====================================================
    // PARSE INTEGER
    // =====================================================

    private static int parseInt(
        String value,
        int fallback
    ) {

        try {

            return Integer.parseInt(
                value
            );

        } catch (Exception e) {

            return fallback;
        }
    }


    // =====================================================
    // WEBSITE
    // =====================================================

    private static void handleWebsite(
        HttpExchange exchange
    ) throws IOException {

        String path =
            exchange
                .getRequestURI()
                .getPath();


        if (path.equals("/")) {

            path =
                "/index.html";
        }


        Path file =
            PUBLIC_DIR
                .resolve(
                    path.substring(1)
                )
                .normalize();


        if (
            !file.startsWith(
                PUBLIC_DIR
            )
            ||
            !Files.exists(file)
            ||
            Files.isDirectory(file)
        ) {

            send(
                exchange,
                404,
                "404 Not Found",
                "text/plain"
            );

            return;
        }


        byte[] data =
            Files.readAllBytes(
                file
            );


        String type =
            "text/html; charset=UTF-8";


        if (
            path.endsWith(".css")
        ) {

            type =
                "text/css; charset=UTF-8";
        }


        if (
            path.endsWith(".js")
        ) {

            type =
                "application/javascript; charset=UTF-8";
        }


        if (
            path.endsWith(".png")
        ) {

            type =
                "image/png";
        }


        if (
            path.endsWith(".jpg")
            ||
            path.endsWith(".jpeg")
        ) {

            type =
                "image/jpeg";
        }


        exchange
            .getResponseHeaders()
            .set(
                "Content-Type",
                type
            );


        exchange.sendResponseHeaders(
            200,
            data.length
        );


        try (
            OutputStream output =
                exchange
                    .getResponseBody()
        ) {

            output.write(data);
        }
    }


    // =====================================================
    // SEND RESPONSE
    // =====================================================

    private static void send(
        HttpExchange exchange,
        int status,
        String body,
        String type
    ) throws IOException {

        byte[] data =
            body.getBytes(
                StandardCharsets.UTF_8
            );


        exchange
            .getResponseHeaders()
            .set(
                "Content-Type",
                type
            );


        exchange.sendResponseHeaders(
            status,
            data.length
        );


        try (
            OutputStream output =
                exchange
                    .getResponseBody()
        ) {

            output.write(data);
        }
    }
}