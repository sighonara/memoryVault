import type { CodegenConfig } from '@graphql-codegen/cli';

const config: CodegenConfig = {
  schema: '../src/main/resources/graphql/*.graphqls',
  documents: 'src/**/*.graphql',
  generates: {
    'src/app/shared/graphql/generated.ts': {
      plugins: [
        'typescript',
        'typescript-operations',
        'typescript-apollo-angular',
      ],
    },
  },
};

export default config;