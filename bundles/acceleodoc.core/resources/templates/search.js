/**
 * acceleodoc search widget
 *
 * Loads the pre-built search-index.json, builds a Lunr index with
 * field-level boosting (name > moduleName > description > params),
 * and wires up the search input to display live results.
 */
(function () {
    'use strict';

    var searchData   = null;
    var lunrIndex    = null;
    var dataByRef    = {};
    var input        = document.getElementById('search-input');
    var dropdown     = document.getElementById('search-results');

    if (!input || !dropdown) return;

    // ── Load index data ─────────────────────────────────────────────────────
    fetch('search-index.json')
        .then(function (r) { return r.json(); })
        .then(function (data) {
            searchData = data;
            data.forEach(function (entry) { dataByRef[entry.id] = entry; });

            lunrIndex = lunr(function () {
                this.ref('id');
                this.field('name',        { boost: 20 });
                this.field('moduleName',  { boost: 10 });
                this.field('description', { boost: 5  });
                this.field('params',      { boost: 2  });

                data.forEach(function (entry) { this.add(entry); }, this);
            });
        })
        .catch(function (err) {
            console.warn('[acceleodoc] Could not load search-index.json:', err);
        });

    // ── Event handling ───────────────────────────────────────────────────────
    input.addEventListener('input', function () {
        var query = input.value.trim();
        if (!query || !lunrIndex) {
            hideDropdown();
            return;
        }
        var results = lunrIndex.search(query + '*');
        renderResults(results, query);
    });

    input.addEventListener('keydown', function (e) {
        if (e.key === 'Escape') { hideDropdown(); input.blur(); }
    });

    document.addEventListener('click', function (e) {
        if (!input.contains(e.target) && !dropdown.contains(e.target)) {
            hideDropdown();
        }
    });

    // ── Rendering ────────────────────────────────────────────────────────────
    function renderResults(results, query) {
        dropdown.innerHTML = '';

        if (results.length === 0) {
            dropdown.innerHTML = '<div class="search-no-results">No results for <em>' + escHtml(query) + '</em></div>';
            showDropdown();
            return;
        }

        var limit = Math.min(results.length, 12);
        for (var i = 0; i < limit; i++) {
            var entry = dataByRef[results[i].ref];
            if (!entry) continue;

            var item = document.createElement('a');
            item.href = entry.url;
            item.className = 'search-result-item';

            var desc = entry.description
                ? entry.description.substring(0, 80) + (entry.description.length > 80 ? '…' : '')
                : '';

            item.innerHTML =
                '<div>' +
                '<span class="result-name">' + escHtml(entry.name) + '</span>' +
                '<span class="result-module">' + escHtml(entry.moduleName) + '</span>' +
                '<span class="badge kind-' + escHtml(entry.kind) + '" style="margin-left:6px;font-size:10px">' + escHtml(entry.kind) + '</span>' +
                '</div>' +
                (desc ? '<div class="result-desc">' + escHtml(desc) + '</div>' : '');

            dropdown.appendChild(item);
        }

        showDropdown();
    }

    function showDropdown() { dropdown.classList.add('visible'); }
    function hideDropdown()  { dropdown.classList.remove('visible'); dropdown.innerHTML = ''; }

    function escHtml(str) {
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }
}());

/* NOTE: lunr.min.js must be placed next to this file in the output directory.
 * Download from: https://unpkg.com/lunr/lunr.min.js
 * Version tested: 2.3.9
 * Add to acceleodoc.core/resources/templates/lunr.min.js before building.
 */
