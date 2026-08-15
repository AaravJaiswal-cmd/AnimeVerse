const genres = [
    "All", "Action", "Adventure", "Comedy", "Drama", "Fantasy",
    "Romance", "Sci-Fi", "Mystery", "Psychological", "Sports",
    "Supernatural", "Horror", "Thriller", "Music", "Slice of Life"
];

let activeGenre = "All";
let activeStatus = "";
let currentSearch = "";
let currentPage = 1;
let hasNextPage = false;

const genreButtons = document.getElementById("genreButtons");
const animeGrid = document.getElementById("animeGrid");
const resultCount = document.getElementById("resultCount");
const resultsTitle = document.getElementById("resultsTitle");
const statusEl = document.getElementById("status");
const searchInput = document.getElementById("searchInput");
const sortSelect = document.getElementById("sortSelect");
const surpriseBtn = document.getElementById("surpriseBtn");
const prevBtn = document.getElementById("prevBtn");
const nextBtn = document.getElementById("nextBtn");
const pageText = document.getElementById("pageText");
const pageLabel = document.getElementById("pageLabel");

function createGenreButtons() {
    genreButtons.innerHTML = genres.map(genre => `
        <button class="genre-btn ${genre === activeGenre ? "active" : ""}"
                data-genre="${escapeHTML(genre)}">
            ${escapeHTML(genre)}
        </button>
    `).join("");

    document.querySelectorAll(".genre-btn").forEach(button => {
        button.addEventListener("click", () => {
            activeGenre = button.dataset.genre;
            currentPage = 1;
            createGenreButtons();
            loadAnime();
        });
    });
}

document.querySelectorAll(".quick-btn").forEach(button => {
    button.addEventListener("click", () => {
        activeStatus = button.dataset.status;
        currentPage = 1;

        document.querySelectorAll(".quick-btn").forEach(btn => {
            btn.classList.toggle("active", btn === button);
        });

        loadAnime();
    });
});

async function loadAnime() {
    statusEl.textContent = "Fetching live anime data...";

    animeGrid.innerHTML = `
        <div class="loading">
            <span></span><span></span><span></span>
        </div>
    `;

    try {
        const params = new URLSearchParams();

        params.set("page", currentPage);
        params.set("search", currentSearch);
        params.set("genre", activeGenre === "All" ? "" : activeGenre);
        params.set("status", activeStatus);
        params.set("sort", convertSort(sortSelect.value));

        const response = await fetch(`/api/anime?${params}`, {
            cache: "no-store"
        });

        const result = await response.json();

        if (!response.ok || result.error) {
            throw new Error(
                result.error || `HTTP ${response.status}`
            );
        }

        const anime = result.data || [];

        hasNextPage =
            result.pagination &&
            result.pagination.has_next_page;

        renderAnime(anime);

        resultCount.textContent = `${anime.length} titles`;
        pageText.textContent = `Page ${currentPage}`;
        pageLabel.textContent = `Page ${currentPage}`;

        prevBtn.disabled = currentPage <= 1;
        nextBtn.disabled = !hasNextPage;

        statusEl.textContent =
            `LIVE • ${anime.length} anime loaded`;

    } catch (error) {
        console.error(error);

        animeGrid.innerHTML = `
            <div class="empty">
                <strong>Live data could not be loaded.</strong>
                <br><br>
                ${escapeHTML(error.message)}
            </div>
        `;

        statusEl.textContent = "Live API connection failed.";
        nextBtn.disabled = true;
    }
}

function renderAnime(animeList) {
    if (!animeList.length) {
        animeGrid.innerHTML = `
            <div class="empty">
                <strong>No anime found.</strong>
                <br>
                Try another genre or search term.
            </div>
        `;
        return;
    }

    animeGrid.innerHTML = animeList.map(anime => {
        const title =
            anime.title_english ||
            anime.title ||
            "Unknown Anime";

        const japanese =
            anime.title_japanese ||
            anime.title ||
            "";

        const score =
            anime.score
                ? anime.score.toFixed(2)
                : "N/A";

        const year =
            anime.year ||
            getYear(anime.aired?.from);

        const episodes =
            anime.episodes ||
            "TBA";

        const image =
            anime.images?.jpg?.large_image_url ||
            anime.images?.jpg?.image_url ||
            "";

        const synopsis =
            anime.synopsis ||
            "No description available.";

        const tags =
            anime.genres?.slice(0, 4)
                .map(genre =>
                    `<span class="tag">${escapeHTML(genre.name)}</span>`
                )
                .join("") || "";

        return `
            <article class="card">
                <div class="poster-wrap">
                    ${
                        image
                        ? `<img class="poster"
                               src="${escapeHTML(image)}"
                               alt="${escapeHTML(title)}"
                               loading="lazy">`
                        : `<div class="poster-fallback">ア</div>`
                    }

                    <div class="card-rating">
                        ★ ${score}
                    </div>
                </div>

                <div class="card-body">
                    <div class="native-title">
                        ${escapeHTML(japanese)}
                    </div>

                    <h3>${escapeHTML(title)}</h3>

                    <div class="year">
                        ${escapeHTML(String(year || "Unknown"))}
                        •
                        ${escapeHTML(anime.status || "Unknown")}
                        •
                        ${episodes} eps
                    </div>

                    <p class="description">
                        ${escapeHTML(synopsis)}
                    </p>

                    <div class="tags">
                        ${tags}
                    </div>
                </div>
            </article>
        `;
    }).join("");
}

function convertSort(value) {
    switch (value) {
        case "SCORE_DESC":
            return "score";
        case "START_DATE_DESC":
            return "newest";
        case "TRENDING_DESC":
            return "trending";
        default:
            return "popularity";
    }
}

async function surpriseMe() {
    surpriseBtn.disabled = true;
    statusEl.textContent = "Finding a random anime...";

    try {
        const response = await fetch("/api/random", {
            cache: "no-store"
        });

        const result = await response.json();

        if (!response.ok || result.error) {
            throw new Error(
                result.error || `HTTP ${response.status}`
            );
        }

        renderAnime([result.data]);

        resultsTitle.textContent = "Your Random Pick";
        resultCount.textContent = "1 anime";
        pageText.textContent = "Random";
        pageLabel.textContent = "Random";

        prevBtn.disabled = true;
        nextBtn.disabled = true;

        statusEl.textContent =
            "Fresh recommendation from Jikan.";

    } catch (error) {
        statusEl.textContent =
            "Could not get a random anime.";
        console.error(error);
    } finally {
        surpriseBtn.disabled = false;
    }
}

searchInput.addEventListener("input", event => {
    currentSearch = event.target.value.trim();
    currentPage = 1;

    clearTimeout(window.searchTimer);

    window.searchTimer = setTimeout(loadAnime, 400);
});

sortSelect.addEventListener("change", () => {
    currentPage = 1;
    loadAnime();
});

prevBtn.addEventListener("click", () => {
    if (currentPage > 1) {
        currentPage--;
        loadAnime();
        window.scrollTo({
            top: document.querySelector(".recommendation-panel").offsetTop - 20,
            behavior: "smooth"
        });
    }
});

nextBtn.addEventListener("click", () => {
    if (hasNextPage) {
        currentPage++;
        loadAnime();
        window.scrollTo({
            top: document.querySelector(".recommendation-panel").offsetTop - 20,
            behavior: "smooth"
        });
    }
});

surpriseBtn.addEventListener("click", surpriseMe);

function getYear(date) {
    if (!date) return "Unknown";
    return new Date(date).getFullYear();
}

function escapeHTML(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

createGenreButtons();
loadAnime();
