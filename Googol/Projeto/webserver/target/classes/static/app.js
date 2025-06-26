var stompClient = null;

function setConnected(connected) {
        document.getElementById("conversation").style.display = 'block';
    document.getElementById("messages").innerHTML = "";
}

function sendMessage() {
    const messageValue = document.getElementById("message").value;
    console.log("Sending message:", messageValue);  // Debugging line

    window.location.href = ("/search?message=" + encodeURIComponent(messageValue) +"&page=0");
}

function links() {
    const messageValue = document.getElementById("message").value;
    console.log("Sending message:", messageValue);  // Debugging line

    window.location.href = ("/links?page=" + encodeURIComponent(messageValue));
}


function add() {
    const messageValue = document.getElementById("message").value;

    fetch("/add", {
        method: "POST",
        headers: {
            "Content-Type": "application/json"
        },
        body: JSON.stringify({ content: messageValue })
    })
}

function test() {
    const messageValue = document.getElementById("message").value;

        fetch("/top", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ content: messageValue })
        })
}


//@ahf: Bye Bye Jquery
window.addEventListener('load',
    function () {
        //@ahf: So many prevent defaults. Why even use forms?
        document.getElementById("send").addEventListener('click', (e) => {
            e.preventDefault();
            sendMessage();
        });
        document.getElementById("top").addEventListener('click', (e) => {
            e.preventDefault();
            test();
        });
        document.getElementById("add").addEventListener('click', (e) => {
                    e.preventDefault();
                    add();
                });
        document.getElementById("links").addEventListener('click', (e) => {
                            e.preventDefault();
                            links();
                        });

    }, false);