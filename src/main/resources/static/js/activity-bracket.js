(function (root, factory) {
    const api = factory(root);
    if (typeof module === 'object' && module.exports) {
        module.exports = api;
    }
    root.ActivityBracket = api;
}(typeof globalThis !== 'undefined' ? globalThis : this, function (root) {
    const MAX_ENTRANTS = 128;

    function buildRounds(signups) {
        const participants = shuffle(signups);
        let bracketSize = 2;
        while (bracketSize < participants.length) bracketSize *= 2;

        const firstRoundMatchCount = bracketSize / 2;
        const contestedMatchCount = participants.length - firstRoundMatchCount;
        const matchTypes = [];
        for (let index = 0; index < firstRoundMatchCount; index++) {
            matchTypes.push(index < contestedMatchCount);
        }

        const rounds = [];
        let participantIndex = 0;
        let matchNumber = 1;
        const firstRound = shuffle(matchTypes).map(contested => {
            const first = formatParticipant(participants[participantIndex++]);
            const second = contested
                    ? formatParticipant(participants[participantIndex++])
                    : '轮空';
            return { id: matchNumber++, players: [first, second] };
        });
        rounds.push(firstRound);

        let previousRound = firstRound;
        while (previousRound.length > 1) {
            const nextRound = [];
            for (let index = 0; index < previousRound.length; index += 2) {
                nextRound.push({
                    id: matchNumber++,
                    players: [
                        `第 ${previousRound[index].id} 场胜者`,
                        `第 ${previousRound[index + 1].id} 场胜者`
                    ]
                });
            }
            rounds.push(nextRound);
            previousRound = nextRound;
        }
        return rounds;
    }

    function shuffle(values) {
        const shuffled = values.slice();
        for (let index = shuffled.length - 1; index > 0; index--) {
            const randomIndex = secureRandomIndex(index + 1);
            [shuffled[index], shuffled[randomIndex]] = [shuffled[randomIndex], shuffled[index]];
        }
        return shuffled;
    }

    function secureRandomIndex(maxExclusive) {
        const cryptoApi = root.crypto || root.msCrypto;
        if (!cryptoApi || !cryptoApi.getRandomValues) {
            return Math.floor(Math.random() * maxExclusive);
        }

        const range = 0x100000000;
        const limit = range - (range % maxExclusive);
        const values = new Uint32Array(1);
        do {
            cryptoApi.getRandomValues(values);
        } while (values[0] >= limit);
        return values[0] % maxExclusive;
    }

    function formatParticipant(signup) {
        const nickname = String(signup.nickname || '').trim();
        const name = String(signup.name || '').trim();
        if (nickname && name && nickname !== name) return `${nickname}（${name}）`;
        return nickname || name || '未命名用户';
    }

    function draw(canvas, signups, activity) {
        if (signups.length < 2 || signups.length > MAX_ENTRANTS) {
            throw new RangeError(`签表人数必须在2到${MAX_ENTRANTS}人之间`);
        }

        const rounds = buildRounds(signups);
        const boxWidth = 210;
        const boxHeight = 66;
        const columnGap = 90;
        const leftMargin = 56;
        const headerHeight = 140;
        const footerHeight = 50;
        const firstRoundCount = rounds[0].length;
        const contentHeight = Math.max(400, firstRoundCount * 100);
        const logicalWidth = leftMargin * 2
                + (rounds.length + 1) * boxWidth
                + rounds.length * columnGap;
        const logicalHeight = headerHeight + contentHeight + footerHeight;
        const maxPixels = 16000000;
        const maxDimension = 16384;
        const scale = Math.max(1, Math.min(
                2,
                Math.sqrt(maxPixels / (logicalWidth * logicalHeight)),
                maxDimension / logicalWidth,
                maxDimension / logicalHeight));

        canvas.width = Math.round(logicalWidth * scale);
        canvas.height = Math.round(logicalHeight * scale);
        canvas.style.width = `${logicalWidth}px`;
        canvas.style.height = `${logicalHeight}px`;

        const context = canvas.getContext('2d');
        context.setTransform(scale, 0, 0, scale, 0, 0);
        context.fillStyle = '#ffffff';
        context.fillRect(0, 0, logicalWidth, logicalHeight);

        drawHeader(context, activity, signups.length, logicalWidth);

        const positions = [];
        const firstSpacing = contentHeight / firstRoundCount;
        positions[0] = rounds[0].map((match, index) => ({
            x: leftMargin,
            y: headerHeight + firstSpacing * (index + 0.5) - boxHeight / 2,
            centerY: headerHeight + firstSpacing * (index + 0.5)
        }));

        for (let roundIndex = 1; roundIndex < rounds.length; roundIndex++) {
            const x = leftMargin + roundIndex * (boxWidth + columnGap);
            positions[roundIndex] = rounds[roundIndex].map((match, index) => {
                const upper = positions[roundIndex - 1][index * 2].centerY;
                const lower = positions[roundIndex - 1][index * 2 + 1].centerY;
                const centerY = (upper + lower) / 2;
                return { x, y: centerY - boxHeight / 2, centerY };
            });
        }

        rounds.forEach((round, roundIndex) => {
            drawRoundTitle(context, roundTitle(roundIndex, rounds.length),
                    positions[roundIndex][0].x, boxWidth);
            round.forEach((match, matchIndex) => {
                drawMatchBox(context, match, positions[roundIndex][matchIndex], boxWidth, boxHeight);
            });
        });

        for (let roundIndex = 1; roundIndex < rounds.length; roundIndex++) {
            positions[roundIndex].forEach((target, index) => {
                drawConnector(context, positions[roundIndex - 1][index * 2], target,
                        boxWidth, boxHeight, true);
                drawConnector(context, positions[roundIndex - 1][index * 2 + 1], target,
                        boxWidth, boxHeight, false);
            });
        }

        const finalRoundIndex = rounds.length - 1;
        const finalPosition = positions[finalRoundIndex][0];
        const championPosition = {
            x: finalPosition.x + boxWidth + columnGap,
            y: finalPosition.centerY - boxHeight / 2,
            centerY: finalPosition.centerY
        };
        drawRoundTitle(context, '冠军', championPosition.x, boxWidth);
        drawChampionBox(context, rounds[finalRoundIndex][0], championPosition, boxWidth, boxHeight);
        drawChampionConnector(context, finalPosition, championPosition, boxWidth);
        return rounds;
    }

    function drawHeader(context, activity, entrantCount, width) {
        context.textAlign = 'center';
        context.textBaseline = 'middle';
        context.fillStyle = '#123047';
        context.font = 'bold 26px "Microsoft YaHei", sans-serif';
        context.fillText(`${activity.title || '活动'}比赛签表`, width / 2, 34);

        context.fillStyle = '#526274';
        context.font = '14px "Microsoft YaHei", sans-serif';
        const time = activity.activityTime || '时间待定';
        const location = activity.location || '地点待定';
        context.fillText(`${time}  ·  ${location}`, width / 2, 70);
        context.fillText(`参赛人数：${entrantCount}  ·  抽签时间：${formatDrawTime(new Date())}`,
                width / 2, 96);
    }

    function drawRoundTitle(context, title, x, width) {
        context.textAlign = 'center';
        context.textBaseline = 'middle';
        context.fillStyle = '#3d4b5f';
        context.font = 'bold 15px "Microsoft YaHei", sans-serif';
        context.fillText(title, x + width / 2, 122);
    }

    function roundTitle(roundIndex, roundCount) {
        if (roundIndex === roundCount - 1) return '决赛';
        if (roundIndex === roundCount - 2) return '半决赛';
        if (roundIndex === roundCount - 3) return '四分之一决赛';
        return `第 ${roundIndex + 1} 轮`;
    }

    function drawMatchBox(context, match, position, width, height) {
        context.fillStyle = '#ffffff';
        context.strokeStyle = '#8190a2';
        context.lineWidth = 1.5;
        context.beginPath();
        context.rect(position.x, position.y, width, height);
        context.fill();
        context.stroke();

        context.beginPath();
        context.moveTo(position.x, position.y + height / 2);
        context.lineTo(position.x + width, position.y + height / 2);
        context.stroke();

        context.fillStyle = '#007fa4';
        context.font = 'bold 11px "Microsoft YaHei", sans-serif';
        context.textAlign = 'left';
        context.fillText(`M${match.id}`, position.x + 7, position.y - 9);

        context.fillStyle = '#172b3a';
        context.font = '13px "Microsoft YaHei", sans-serif';
        drawFittedText(context, match.players[0], position.x + 10,
                position.y + height / 4, width - 20);
        context.fillStyle = match.players[1] === '轮空' ? '#8a5a00' : '#172b3a';
        drawFittedText(context, match.players[1], position.x + 10,
                position.y + height * 3 / 4, width - 20);
    }

    function drawFittedText(context, text, x, y, maxWidth) {
        let fitted = String(text);
        if (context.measureText(fitted).width > maxWidth) {
            while (fitted.length > 1 && context.measureText(`${fitted}…`).width > maxWidth) {
                fitted = fitted.slice(0, -1);
            }
            fitted += '…';
        }
        context.textAlign = 'left';
        context.textBaseline = 'middle';
        context.fillText(fitted, x, y);
    }

    function drawConnector(context, source, target, boxWidth, boxHeight, upperInput) {
        const startX = source.x + boxWidth;
        const endX = target.x;
        const middleX = startX + (endX - startX) / 2;
        const endY = target.y + (upperInput ? boxHeight / 4 : boxHeight * 3 / 4);
        context.strokeStyle = '#6d7d8f';
        context.lineWidth = 1.5;
        context.beginPath();
        context.moveTo(startX, source.centerY);
        context.lineTo(middleX, source.centerY);
        context.lineTo(middleX, endY);
        context.lineTo(endX, endY);
        context.stroke();
    }

    function drawChampionBox(context, finalMatch, position, width, height) {
        context.fillStyle = '#fff4cc';
        context.strokeStyle = '#b27b00';
        context.lineWidth = 2;
        context.beginPath();
        context.rect(position.x, position.y, width, height);
        context.fill();
        context.stroke();

        context.fillStyle = '#684600';
        context.textAlign = 'center';
        context.textBaseline = 'middle';
        context.font = 'bold 15px "Microsoft YaHei", sans-serif';
        context.fillText('冠军', position.x + width / 2, position.y + height / 3);
        context.font = '13px "Microsoft YaHei", sans-serif';
        context.fillText(`第 ${finalMatch.id} 场胜者`,
                position.x + width / 2, position.y + height * 2 / 3);
    }

    function drawChampionConnector(context, source, target, boxWidth) {
        context.strokeStyle = '#b27b00';
        context.lineWidth = 2;
        context.beginPath();
        context.moveTo(source.x + boxWidth, source.centerY);
        context.lineTo(target.x, target.centerY);
        context.stroke();
    }

    function formatDrawTime(date) {
        const pad = value => String(value).padStart(2, '0');
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} `
                + `${pad(date.getHours())}:${pad(date.getMinutes())}`;
    }

    return { MAX_ENTRANTS, draw };
}));
