(function () {
    const dialog = document.getElementById('generateDialog');
    const form = document.getElementById('generateForm');
    const openModalBtn = document.getElementById('openModalBtn');
    const cancelBtn = document.getElementById('cancelBtn');
    const refreshHistoryBtn = document.getElementById('refreshHistoryBtn');
    const verifyDialog = document.getElementById('verifyDialog');
    const openVerifyModalBtn = document.getElementById('openVerifyModalBtn');
    const closeVerifyDialogBtn = document.getElementById('closeVerifyDialogBtn');
    const progressWrap = document.getElementById('progressWrap');
    const jobProgress = document.getElementById('jobProgress');
    const jobStatus = document.getElementById('jobStatus');
    const downloadWrap = document.getElementById('downloadWrap');
    const historyList = document.getElementById('historyList');
    const funStatsSummary = document.getElementById('funStatsSummary');
    const funStatsList = document.getElementById('funStatsList');
    const gameWrap = document.getElementById('gameWrap');
    const numbersBoard = document.getElementById('numbersBoard');
    const verifySeriesNumber = document.getElementById('verifySeriesNumber');
    const verifySeriesBtn = document.getElementById('verifySeriesBtn');
    const verifyMessage = document.getElementById('verifyMessage');
    const verifyResult = document.getElementById('verifyResult');

    let pollHandle = null;
    let currentJobId = null;
    const extractedNumbers = new Set();

    renderBoard();
    loadHistory();
    loadFunStats();

    openModalBtn.addEventListener('click', function () {
        dialog.showModal();
    });

    cancelBtn.addEventListener('click', function () {
        dialog.close();
    });

    refreshHistoryBtn.addEventListener('click', function () {
        loadHistory();
        loadFunStats();
    });

    openVerifyModalBtn.addEventListener('click', function () {
        if (!currentJobId) {
            return;
        }
        verifyDialog.showModal();
    });

    closeVerifyDialogBtn.addEventListener('click', function () {
        verifyDialog.close();
    });

    form.addEventListener('submit', async function (event) {
        event.preventDefault();

        const payload = {
            seriesCount: Number(document.getElementById('seriesCount').value),
            maxWaitSeconds: Number(document.getElementById('maxWaitSeconds').value)
        };

        const seedValue = document.getElementById('seed').value.trim();
        if (seedValue) {
            payload.seed = Number(seedValue);
        }

        const maxAttemptsValue = document.getElementById('maxSeriesAttempts').value.trim();
        if (maxAttemptsValue) {
            payload.maxSeriesAttempts = Number(maxAttemptsValue);
        }

        try {
            const response = await fetch('/api/jobs', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(errorText || 'Errore durante l\'avvio del job');
            }

            const started = await response.json();
            dialog.close();
            startPolling(started.jobId);
        } catch (error) {
            jobStatus.textContent = 'Errore: ' + error.message;
            progressWrap.style.display = 'block';
        }
    });

    function startPolling(jobId) {
        currentJobId = jobId;
        progressWrap.style.display = 'block';
        jobProgress.value = 0;
        jobStatus.textContent = 'Job avviato...';
        downloadWrap.innerHTML = '';
        resetGameState();

        if (pollHandle) {
            clearInterval(pollHandle);
        }

        pollHandle = setInterval(async function () {
            try {
                const response = await fetch('/api/jobs/' + encodeURIComponent(jobId));
                if (!response.ok) {
                    throw new Error('Impossibile leggere lo stato del job');
                }

                const status = await response.json();
                jobProgress.value = status.progress;
                jobStatus.textContent = status.message + ' (' + status.progress + '%)';

                if (status.state === 'COMPLETED') {
                    clearInterval(pollHandle);
                    pollHandle = null;
                    downloadWrap.innerHTML = '<a href="' + status.downloadUrl + '">Scarica ' + status.fileName + '</a>';
                    prepareGameForJob(jobId, status.seriesCount || 1);
                    await refreshAuxiliaryPanels();
                } else if (status.state === 'FAILED') {
                    clearInterval(pollHandle);
                    pollHandle = null;
                    await refreshAuxiliaryPanels();
                }
            } catch (error) {
                clearInterval(pollHandle);
                pollHandle = null;
                jobStatus.textContent = 'Errore polling: ' + error.message;
            }
        }, 1000);
    }

    verifySeriesBtn.addEventListener('click', async function () {
        if (!currentJobId) {
            return;
        }

        verifyMessage.textContent = 'Verifica in corso...';
        verifyResult.innerHTML = '';

        const payload = {
            seriesNumber: Number(verifySeriesNumber.value),
            extractedNumbers: Array.from(extractedNumbers).sort(function (a, b) {
                return a - b;
            })
        };

        try {
            const response = await fetch('/api/jobs/' + encodeURIComponent(currentJobId) + '/verify', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(errorText || 'Errore durante la verifica');
            }

            const result = await response.json();
            renderVerificationResult(result);
            verifyMessage.textContent = 'Serie ' + result.seriesNumber + ': cartelle aggiornate con i numeri estratti.';
        } catch (error) {
            verifyMessage.textContent = 'Errore verifica: ' + error.message;
        }
    });

    function renderBoard() {
        numbersBoard.innerHTML = '';
        for (let value = 1; value <= 90; value++) {
            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'board-btn';
            button.textContent = String(value);
            button.dataset.value = String(value);
            button.addEventListener('click', function () {
                const number = Number(button.dataset.value);
                if (extractedNumbers.has(number)) {
                    extractedNumbers.delete(number);
                    button.classList.remove('is-drawn');
                } else {
                    extractedNumbers.add(number);
                    button.classList.add('is-drawn');
                }
            });
            numbersBoard.appendChild(button);
        }
    }

    function resetGameState() {
        extractedNumbers.clear();
        gameWrap.classList.remove('is-visible');
        if (verifyDialog.open) {
            verifyDialog.close();
        }
        verifyMessage.textContent = '';
        verifyResult.innerHTML = '';
        numbersBoard.querySelectorAll('.board-btn').forEach(function (button) {
            button.classList.remove('is-drawn');
        });
    }

    function prepareGameForJob(jobId, seriesCount) {
        currentJobId = jobId;
        extractedNumbers.clear();
        verifyMessage.textContent = '';
        verifyResult.innerHTML = '';
        numbersBoard.querySelectorAll('.board-btn').forEach(function (button) {
            button.classList.remove('is-drawn');
        });
        gameWrap.classList.add('is-visible');
        verifySeriesNumber.max = String(seriesCount || 1);
        verifySeriesNumber.value = '1';
    }

    async function refreshAuxiliaryPanels() {
        await Promise.all([loadHistory(), loadFunStats()]);
    }

    async function loadHistory() {
        historyList.innerHTML = '<p class="muted">Aggiornamento storico in corso...</p>';
        try {
            const response = await fetch('/api/history?limit=8');
            if (!response.ok) {
                throw new Error('Impossibile caricare lo storico');
            }
            const entries = await response.json();
            renderHistory(entries || []);
        } catch (error) {
            historyList.innerHTML = '<p class="muted">' + escapeHtml(error.message) + '</p>';
        }
    }

    async function loadFunStats() {
        funStatsSummary.textContent = 'Aggiornamento statistiche in corso...';
        funStatsList.innerHTML = '';
        try {
            const response = await fetch('/api/stats/fun');
            if (!response.ok) {
                throw new Error('Impossibile caricare le statistiche');
            }
            const stats = await response.json();
            renderFunStats(stats);
        } catch (error) {
            funStatsSummary.textContent = 'Errore statistiche: ' + error.message;
        }
    }

    function renderHistory(entries) {
        historyList.innerHTML = '';
        if (!entries.length) {
            historyList.innerHTML = '<p class="muted">Nessuna generazione storica disponibile.</p>';
            return;
        }

        entries.forEach(function (entry) {
            const article = document.createElement('article');
            article.className = 'history-item';

            const header = document.createElement('div');
            header.className = 'history-item-header';

            const title = document.createElement('div');
            title.className = 'history-item-title';
            title.textContent = (entry.fileName || entry.jobId) + ' • seed ' + (entry.seed != null ? entry.seed : 'n/d');
            header.appendChild(title);

            const badge = document.createElement('span');
            badge.className = 'state-badge ' + stateBadgeClass(entry.state);
            badge.textContent = stateLabel(entry.state);
            header.appendChild(badge);

            article.appendChild(header);

            const meta = document.createElement('div');
            meta.className = 'history-meta';
            meta.innerHTML = [
                '<span>Serie: ' + escapeHtml(String(entry.seriesCount || 0)) + '</span>',
                '<span>Durata: ' + escapeHtml(formatDuration(entry.durationMillis)) + '</span>',
                '<span>Concluso: ' + escapeHtml(formatDateTime(entry.completedAt || entry.startedAt)) + '</span>'
            ].join('');
            article.appendChild(meta);

            const message = document.createElement('p');
            message.className = 'muted';
            message.textContent = entry.message || 'Nessun dettaglio disponibile';
            article.appendChild(message);

            const actions = document.createElement('div');
            actions.className = 'history-actions';

            if (entry.downloadUrl) {
                const link = document.createElement('a');
                link.href = entry.downloadUrl;
                link.textContent = 'Scarica PDF';
                actions.appendChild(link);
            }

            if (entry.state === 'COMPLETED' && entry.seriesAvailable) {
                const verifyButton = document.createElement('button');
                verifyButton.type = 'button';
                verifyButton.className = 'secondary';
                verifyButton.textContent = 'Usa per verifica';
                verifyButton.addEventListener('click', function () {
                    downloadWrap.innerHTML = entry.downloadUrl
                        ? '<a href="' + entry.downloadUrl + '">Scarica ' + entry.fileName + '</a>'
                        : '';
                    prepareGameForJob(entry.jobId, entry.seriesCount || 1);
                    jobStatus.textContent = 'Storico attivo: ' + (entry.message || entry.fileName || entry.jobId);
                    progressWrap.style.display = 'block';
                    jobProgress.value = entry.progress || 100;
                });
                actions.appendChild(verifyButton);
            }

            article.appendChild(actions);
            historyList.appendChild(article);
        });
    }

    function renderFunStats(stats) {
        const totalJobs = Number(stats.totalJobs || 0);
        const completedJobs = Number(stats.completedJobs || 0);
        const failedJobs = Number(stats.failedJobs || 0);
        const successRatePercent = Number(stats.successRatePercent || 0);
        const averageDurationMillis = Number(stats.averageDurationMillis || 0);
        funStatsSummary.textContent = 'Totale job: ' + totalJobs
            + ' • completati: ' + completedJobs
            + ' • falliti: ' + failedJobs
            + ' • successo: ' + successRatePercent + '%'
            + ' • durata media: ' + formatDuration(averageDurationMillis);

        funStatsList.innerHTML = '';
        const cards = Array.isArray(stats.highlights) ? stats.highlights : [];
        if (!cards.length) {
            funStatsList.innerHTML = '<div class="stat-card"><h3>Ancora nessun record</h3><p>Genera qualche cartella per sbloccare le curiosità.</p></div>';
            return;
        }

        cards.forEach(function (card) {
            const element = document.createElement('div');
            element.className = 'stat-card';

            const title = document.createElement('h3');
            title.textContent = card.title;
            element.appendChild(title);

            const value = document.createElement('div');
            value.className = 'stat-value';
            value.textContent = card.value;
            element.appendChild(value);

            const description = document.createElement('p');
            description.textContent = card.description;
            element.appendChild(description);

            funStatsList.appendChild(element);
        });
    }

    function formatDuration(durationMillis) {
        const value = Number(durationMillis || 0);
        if (!value) {
            return 'n/d';
        }
        if (value < 1000) {
            return value + ' ms';
        }
        const seconds = Math.floor(value / 1000);
        const remainder = value % 1000;
        return seconds + ',' + String(remainder).padStart(3, '0') + ' s';
    }

    function formatDateTime(value) {
        if (!value) {
            return 'n/d';
        }
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return 'n/d';
        }
        return date.toLocaleString('it-IT');
    }

    function stateBadgeClass(state) {
        switch (state) {
            case 'COMPLETED':
                return 'state-completed';
            case 'FAILED':
                return 'state-failed';
            default:
                return 'state-running';
        }
    }

    function stateLabel(state) {
        switch (state) {
            case 'COMPLETED':
                return 'Completato';
            case 'FAILED':
                return 'Fallito';
            case 'RUNNING':
                return 'In corso';
            case 'PENDING':
                return 'In coda';
            default:
                return state || 'Sconosciuto';
        }
    }

    function escapeHtml(value) {
        return String(value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function renderVerificationResult(result) {
        verifyResult.innerHTML = '';
        (result.cards || []).forEach(function (card) {
            const cardWrap = document.createElement('div');
            cardWrap.className = 'card';

            const title = document.createElement('div');
            title.className = 'card-title';
            title.textContent = 'Cartella ' + card.cardNumber;
            cardWrap.appendChild(title);

            const table = document.createElement('table');
            table.className = 'card-grid';

            (card.rows || []).forEach(function (row) {
                const tr = document.createElement('tr');
                (row || []).forEach(function (cell) {
                    const td = document.createElement('td');
                    const value = Number(cell.value);
                    td.textContent = value > 0 ? String(value) : '';
                    if (cell.drawn) {
                        td.classList.add('drawn');
                    }
                    tr.appendChild(td);
                });
                table.appendChild(tr);
            });

            cardWrap.appendChild(table);
            verifyResult.appendChild(cardWrap);
        });
    }
})();

