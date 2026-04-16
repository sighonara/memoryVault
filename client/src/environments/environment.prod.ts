export const environment = {
  production: true,
  apiUrl: '', // relative URLs, served from same origin
  wsUrl: '', // set at runtime from window.location
  cognito: {
    userPoolId: 'us-east-1_pGZkqbVGk', // replaced at build time or via env
    clientId: '4h6mteuhaccrgu6f03siffalbr',
    region: 'us-east-1',
  },
};
