
document.addEventListener('DOMContentLoaded', function () {
    const input = document.getElementById('message');
    const sendBtn = document.getElementById('send');
    const topBtn = document.getElementById('top');
    const linkBtn = document.getElementById('links');

    function toggleButtons() {
        const hasText = input.value.trim().length > 0;
        sendBtn.disabled = !hasText;
        topBtn.disabled = !hasText;
        linkBtn.disabled = !hasText;
    }

    // Verifica inicialmente
    toggleButtons();

    // Atualiza sempre que o utilizador escreve
    input.addEventListener('input', toggleButtons);
});

