const { tiktokService } = require('./src/services/tiktok.service');
const url = 'https://vm.tiktok.com/ZS9Y96REx6EXV-s1ykM/';

(async () => {
    try {
        const meta = await tiktokService(url);
        console.log("SUCCESS:", meta.description.substring(0, 150));
    } catch (e) {
        console.error("ERROR:", e.message);
    }
})();
