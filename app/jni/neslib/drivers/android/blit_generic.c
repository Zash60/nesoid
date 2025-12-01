#include <stdint.h>

// C implementation of 8-bit to 16-bit blitter for non-ARM32 architectures
void Blit8To16Asm(const uint8_t *src, uint16_t *dst, const uint16_t *palette, int width, int height)
{
    int h, w;
    for (h = 0; h < height; h++) {
        for (w = 0; w < width; w++) {
            *dst++ = palette[*src++];
        }
    }
}

// C implementation of reversed 8-bit to 16-bit blitter
void Blit8To16RevAsm(const uint8_t *src, uint16_t *dst, const uint16_t *palette, int width, int height)
{
    int h, w;
    for (h = 0; h < height; h++) {
        const uint8_t *s = src + width - 1;
        for (w = 0; w < width; w++) {
            *dst++ = palette[*s--];
        }
        src += width;
    }
}
