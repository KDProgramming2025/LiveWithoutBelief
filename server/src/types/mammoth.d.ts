declare module 'mammoth' {
  export interface ConvertImageResult { src: string }
  export interface ImageHandler {
    (image: { contentType?: string; read: (encoding: 'base64' | 'binary') => Promise<string> }): Promise<ConvertImageResult> | ConvertImageResult;
  }
  export const images: {
    inline: (handler: ImageHandler) => ImageHandler;
  };
  export function convertToHtml(input: { path: string }, options?: { convertImage?: ImageHandler }): Promise<{ value: string }>
  const _default: unknown;
  export default _default;
}
