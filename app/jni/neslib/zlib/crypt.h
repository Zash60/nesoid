/* crypt.h -- stub implementation for zip encryption, used when NOCRYPT is defined
 * This is a placeholder file to allow compilation when encryption is disabled.
 * The original functionality is not present.
 */

#ifndef _CRYPT_H
#define _CRYPT_H

/* Constants needed for encryption */
#define RAND_HEAD_LEN 12

/* Empty stubs for encryption functions */
#define init_keys(pw, keys, pcrc_32_tab) do { }
#define crypthead(password, buf, buf_size, keys, pcrc_32_tab, crcForCrypting) 0
#define zencode(keys, pcrc_32_tab, c, t) (c)

#endif /* _CRYPT_H */
