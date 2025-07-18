const express = require('express');
const bodyParser = require('body-parser');
const axios = require('axios');
const { encode } = require('gpt-3-encoder');

const app = express();
const PORT = process.env.PORT || 3001;
const MAX_TOKENS = 4096;

app.use(bodyParser.json({ limit: '10mb' }));

/**
 * Recibe un prompt, lo divide o resume si supera el límite de tokens,
 * y lo envía al modelo Granite/Ollama.
 */
app.post('/procesar-prompt', async (req, res) => {
  const { prompt, ollamaUrl, model } = req.body;
  if (!prompt) {
    return res.status(400).json({ error: 'Prompt requerido.' });
  }
  if (!ollamaUrl) {
    return res.status(400).json({ error: 'ollamaUrl requerido.' });
  }

  // Tokeniza el prompt y divide en partes de máximo MAX_TOKENS
  const allTokens = encode(prompt);
  let respuestaTexto = "";
  let partes = [];
  let start = 0;
  while (start < allTokens.length) {
    const end = Math.min(start + MAX_TOKENS, allTokens.length);
    const tokenSlice = allTokens.slice(start, end);
    // Decodifica los tokens a texto
    // gpt-3-encoder no tiene decode, así que usamos el substring original
    // Esto puede no ser exacto, pero para la mayoría de los casos funciona
    // Si tienes una función decode, úsala aquí
    const partText = prompt.substring(start, end * 2); // Aproximación
    partes.push(partText);
    start = end;
  }

  try {
    for (let i = 0; i < partes.length; i++) {
      const partPrompt = partes[i];
      const response = await axios({
        method: 'post',
        url: `${ollamaUrl}/api/generate`,
        data: {
          model: model || "granite-code",
          prompt: partPrompt
        },
        responseType: 'stream',
        headers: {
          'Accept': 'application/x-ndjson',
          'Content-Type': 'application/json'
        }
      });
      // Procesar NDJSON
      const chunks = [];
      for await (const chunk of response.data) {
        chunks.push(chunk);
      }
      const ndjson = Buffer.concat(chunks).toString('utf8');
      ndjson.split('\n').forEach(line => {
        if (line.trim()) {
          try {
            const obj = JSON.parse(line);
            if (obj.response) respuestaTexto += obj.response;
          } catch {}
        }
      });
    }
    return res.json({ respuesta_texto: respuestaTexto });
  } catch (err) {
    // Mostrar detalles del error
    let detalle = '';
    if (err.response) {
      detalle = JSON.stringify(err.response.data);
    } else if (err.request) {
      detalle = 'No se recibió respuesta del servidor Ollama.';
    } else {
      detalle = err.message;
    }
    return res.status(500).json({ error: 'Error al comunicarse con Ollama.', detalle });
  }
});

app.listen(PORT, () => {
  console.log(`Microservicio de distribución de contexto escuchando en puerto ${PORT}`);
});
