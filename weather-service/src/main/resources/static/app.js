(function () {
  'use strict';

  var MIN_LENGTH = 2;
  var DEBOUNCE_MS = 300;
  var UNAVAILABLE = 'Search is unavailable, try again';

  var input = document.getElementById('q');
  var statusEl = document.getElementById('status');
  var resultsEl = document.getElementById('results');
  var forecastEl = document.getElementById('forecast');

  var UNIT_KEY = 'weather.unit';
  var toggleEl = document.getElementById('unit-toggle');

  function loadUnit() {
    try {
      return localStorage.getItem(UNIT_KEY) === 'F' ? 'F' : 'C';
    } catch (e) {
      return 'C';
    }
  }

  function saveUnit(u) {
    try { localStorage.setItem(UNIT_KEY, u); } catch (e) { /* ignore */ }
  }

  var CITY_KEY = 'weather.city';

  function loadCity() {
    try {
      var v = localStorage.getItem(CITY_KEY);
      return typeof v === 'string' && v.trim().length >= MIN_LENGTH ? v.trim() : null;
    } catch (e) {
      return null;
    }
  }

  function saveCity(name) {
    try { localStorage.setItem(CITY_KEY, name); } catch (e) { /* ignore */ }
  }

  var unit = loadUnit();
  var lastForecast = null;

  function toDisplay(c) {
    if (typeof c !== 'number' || isNaN(c)) { return ''; }
    var v = unit === 'F' ? c * 9 / 5 + 32 : c;
    return v.toFixed(1);
  }

  var timer = null;
  var searchController = null;
  var forecastController = null;

  function setStatus(text, isError) {
    statusEl.textContent = text;
    statusEl.className = isError ? 'error' : '';
  }

  function clearResults() {
    resultsEl.textContent = '';
  }

  function clearAll() {
    clearResults();
    setStatus('');
  }

  function errorMessage(response, fallback) {
    return response.json().then(function (body) {
      return (body && body.message) || fallback;
    }, function () {
      return fallback;
    });
  }

  function label(city) {
    var parts = [city.name];
    if (city.region) { parts.push(city.region); }
    if (city.country) { parts.push(city.country); }
    return parts.join(', ');
  }

  function renderResults(cities) {
    clearResults();
    cities.forEach(function (city) {
      var li = document.createElement('li');
      var btn = document.createElement('button');
      btn.type = 'button';
      btn.textContent = label(city);
      btn.addEventListener('click', function () { loadForecast(city); });
      li.appendChild(btn);
      resultsEl.appendChild(li);
    });
  }

  function search(q) {
    if (searchController) { searchController.abort(); }
    var controller = new AbortController();
    searchController = controller;
    setStatus('Searching...');
    clearResults();

    fetch('/api/cities/search?query=' + encodeURIComponent(q) + '&limit=5',
          { signal: controller.signal })
      .then(function (response) {
        if (response.ok) {
          return response.json().then(function (body) {
            if (controller !== searchController) { return; }
            var cities = (body && body.results) || [];
            if (cities.length === 0) {
              setStatus('No cities found');
            } else {
              setStatus('');
              renderResults(cities);
            }
          });
        }
        if (response.status === 502) {
          if (controller === searchController) { setStatus(UNAVAILABLE, true); }
          return;
        }
        return errorMessage(response, UNAVAILABLE).then(function (msg) {
          if (controller === searchController) { setStatus(msg, true); }
        });
      })
      .catch(function (err) {
        if (err && err.name === 'AbortError') { return; }
        if (controller === searchController) { setStatus(UNAVAILABLE, true); }
      });
  }

  function cell(row, text, tag) {
    var c = document.createElement(tag || 'td');
    c.textContent = text;
    row.appendChild(c);
  }

  function renderForecast(data) {
    lastForecast = data;
    var symbol = '°' + unit;
    forecastEl.textContent = '';
    var h = document.createElement('h2');
    h.textContent = data.city + ', ' + data.country +
      ' (' + data.latitude + ', ' + data.longitude + ')';
    forecastEl.appendChild(h);

    var table = document.createElement('table');
    var head = document.createElement('tr');
    ['Date', 'Min ' + symbol, 'Max ' + symbol, 'Precip mm', 'Summary'].forEach(function (t) {
      cell(head, t, 'th');
    });
    table.appendChild(head);
    (data.days || []).forEach(function (d) {
      var row = document.createElement('tr');
      cell(row, d.date);
      cell(row, toDisplay(d.minTempC));
      cell(row, toDisplay(d.maxTempC));
      cell(row, d.precipitationMm);
      cell(row, d.summary);
      table.appendChild(row);
    });
    forecastEl.appendChild(table);
    forecastEl.hidden = false;
  }

  function loadForecast(city) {
    if (forecastController) { forecastController.abort(); }
    var controller = new AbortController();
    forecastController = controller;
    forecastEl.hidden = true;
    setStatus('Loading forecast...');

    fetch('/api/forecast?city=' + encodeURIComponent(city.name),
          { signal: controller.signal })
      .then(function (response) {
        if (response.ok) {
          return response.json().then(function (data) {
            if (controller !== forecastController) { return; }
            saveCity(city.name);
            setStatus('');
            renderForecast(data);
          });
        }
        return errorMessage(response, 'Forecast is unavailable, try again')
          .then(function (msg) {
            if (controller === forecastController) { setStatus(msg, true); }
          });
      })
      .catch(function (err) {
        if (err && err.name === 'AbortError') { return; }
        if (controller === forecastController) {
          setStatus('Forecast is unavailable, try again', true);
        }
      });
  }

  function syncToggle() {
    Array.prototype.forEach.call(toggleEl.querySelectorAll('button[data-unit]'), function (b) {
      var active = b.getAttribute('data-unit') === unit;
      b.setAttribute('aria-pressed', active ? 'true' : 'false');
      b.classList.toggle('active', active);
    });
  }

  function setUnit(u) {
    unit = u === 'F' ? 'F' : 'C';
    saveUnit(unit);
    syncToggle();
    if (lastForecast) { renderForecast(lastForecast); }
  }

  toggleEl.addEventListener('click', function (e) {
    var b = e.target.closest ? e.target.closest('button[data-unit]') : null;
    if (b) { setUnit(b.getAttribute('data-unit')); }
  });
  syncToggle();

  var savedCity = loadCity();
  if (savedCity) {
    input.value = savedCity;
    loadForecast({ name: savedCity });
  }

  input.addEventListener('input', function () {
    clearTimeout(timer);
    var q = input.value.trim();
    if (q.length < MIN_LENGTH) {
      if (searchController) { searchController.abort(); searchController = null; }
      clearAll();
      return;
    }
    timer = setTimeout(function () { search(q); }, DEBOUNCE_MS);
  });

  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') { clearResults(); }
  });
})();
