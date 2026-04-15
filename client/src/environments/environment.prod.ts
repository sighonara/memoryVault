export const environment = {
  production: true,
  apiUrl: '', // relative URLs, served from same origin
  wsUrl: '', // set at runtime from window.location
  cognito: {
    userPoolId: 'COGNITO_USER_POOL_ID', // replaced at build time or via env
    clientId: 'COGNITO_CLIENT_ID',
    region: 'us-east-1',
  },
};
