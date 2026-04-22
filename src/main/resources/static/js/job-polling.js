(function () {
    const dialog = document.getElementById('generateDialog');
    const form = document.getElementById('generateForm');
    const openModalBtn = document.getElementById('openModalBtn');
    const cancelBtn = document.getElementById('cancelBtn');
    const progressWrap = document.getElementById('progressWrap');
    const jobProgress = document.getElementById('jobProgress');
    const jobStatus = document.getElementById('jobStatus');
    const downloadWrap = document.getElementById('downloadWrap');
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

    openModalBtn.addEventListener('click', function () {
        dialog.showModal();
    });

    cancelBtn.addEventListener('click', function () {
        dialog.close();
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
                    gameWrap.style.display = 'block';
                    verifySeriesNumber.max = String(status.seriesCount || 1);
                    verifySeriesNumber.value = '1';
                } else if (status.state === 'FAILED') {
                    clearInterval(pollHandle);
                    pollHandle = null;
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
        gameWrap.style.display = 'none';
        verifyMessage.textContent = '';
        verifyResult.innerHTML = '';
        numbersBoard.querySelectorAll('.board-btn').forEach(function (button) {
            button.classList.remove('is-drawn');
        });
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

