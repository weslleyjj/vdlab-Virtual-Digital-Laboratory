var stompClient = null;

let inputs = new Array(18).fill(false);
let buttons = new Array(4).fill(false);

$(document).ready(function (){
    connect();
    $('#painel input').change(function (){
        let idButton = $(this).attr("id");

        inputs[idButton] = !inputs[idButton];

        let numeroComando = getNumberToSend();

        sendCommand(numeroComando);
    });
})

function pressionouBotaoUp(key){
    buttons[key] = false;
    let numeroComando = getNumberToSend();
    sendCommand(numeroComando);
}

function pressionouBotaoDown(key){
    buttons[key] = true;
    let numeroComando = getNumberToSend();
    sendCommand(numeroComando);
}

function getNumberToSend(){

    let binaryString = "";

    inputs.forEach(function(stat, index) {
        // stat = boolean. index = indice no array
        binaryString += (stat) ? '1' : '0';
    });

    buttons.forEach(function (stat, index){
       binaryString += (stat) ? '1' : '0';
    });

    // gera little endian, converte para big endian
    binaryString = binaryString.split("").reverse().join("");

    return parseInt(binaryString, 2);
}


function setConnected(connected) {
    $("#connect").prop("disabled", connected);
    $("#disconnect").prop("disabled", !connected);
    if (connected) {
        $("#conversation").show();
    } else {
        $("#conversation").hide();
    }
    $("#greetings").html("");
}

function connect() {
    var socket = new SockJS('/controlador-placa-socket');
    stompClient = Stomp.over(socket);
    stompClient.debug = () => {};
    stompClient.connect({}, function (frame) {
        setConnected(true);
        // console.log('Connected: ' + frame);
        stompClient.subscribe('/painel/comando', function (msg) {
            if(msg.body == 'EXPIRED'){
                Swal.fire({
                    icon: 'info',
                    title: 'Tempo de sessão esgotado!',
                    html: "<h5>Você será redirecionado para o início</h5>",
                    showConfirmButton: false,
                    timer: 3000
                }).then(() => {
                    window.location.href = "/"
                })
            }
            // showGreeting(JSON.parse(greeting.body).content);
        });
    });
}

function disconnect() {
    if (stompClient !== null) {
        stompClient.disconnect();
    }
    setConnected(false);
    // console.log("Disconnected");
}

function sendName() {
    // stompClient.send("/app/hello", {}, JSON.stringify({'name': $("#name").val()}));
    stompClient.send("/app/comando", {}, $("#name").val());
}

function sendCommand(command){
    stompClient.send("/app/comando", {}, JSON.stringify({'comandoInputs': command, 'placaDesejada': $("#placaConectada").val()}));
}

function showGreeting(message) {
    $("#greetings").append("<tr><td>" + message + "</td></tr>");
}

$(function () {
    $("form").on('submit', function (e) {
        e.preventDefault();
    });
    $("#connect").click(function () {
        connect();
    });
    $("#disconnect").click(function () {
        disconnect();
    });
    $("#send").click(function () {
        sendName();
    });
});
