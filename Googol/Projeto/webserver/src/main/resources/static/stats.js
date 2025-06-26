var stompClient = null;

function setConnected(connected) {
    const statsContainer = document.getElementById("stats-container");
    if (statsContainer) statsContainer.style.display = connected ? 'block' : 'none';
}

function updateTopSearches(data) {
  const list = document.getElementById('topSearches');
  list.innerHTML = '';
  data.forEach(term => {
    const li = document.createElement('li');
    li.className = 'list-group-item';
    li.textContent = term;
    list.appendChild(li);
  });
}

function updateBarrels(data) {
  const tbody = document.getElementById('barrelsTableBody');
  tbody.innerHTML = '';
  data.forEach(barrel => {
    const row = document.createElement('tr');

    const idCell = document.createElement('td');
    idCell.textContent = barrel.id;
    row.appendChild(idCell);

    const sizeCell = document.createElement('td');
    sizeCell.textContent = barrel.indexSize;
    row.appendChild(sizeCell);

    const responseCell = document.createElement('td');
    responseCell.textContent = barrel.responseTime;
    row.appendChild(responseCell);

    tbody.appendChild(row);
  });
}


//@ahf: Bye Bye Jquery
window.addEventListener('load',
    function () {
        //@ahf: So many prevent defaults. Why even use forms?
        var socket = new SockJS('/my-websocket');
            stompClient = Stomp.over(socket);
            stompClient.connect({}, function (frame) {
                setConnected(true);
                console.log('Connected: ' + frame);
                stompClient.subscribe('/topic/statistics', function (message) {
                    console.log("Received message:", message.body);
                    let parsed;
                    try {
                      const stats = JSON.parse(message.body);
                      console.log("Parsed content:", stats);
                        updateTopSearches(stats.topSearches);
                        updateBarrels(stats.barrels);
                    } catch (e) {
                      console.error("Error parsing message body", e);
                    }
                });

            });

    }, false);