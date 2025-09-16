/*
 Lightweight debug logger utilities.
 Use environment variables to toggle specific domains.
 - DEBUG_ALL=1 to enable all
 - DEBUG_MENU=1 to enable menu-related logs (default on when DEBUG_ALL)
 - DEBUG_MULTIPART=1 to enable multipart logs (default on when DEBUG_ALL)
*/

function enabled(flag?: string) {
  if (process.env.DEBUG_ALL === '1') return true
  return flag === '1'
}

export const Debug = {
  menuEnabled(): boolean { return enabled(process.env.DEBUG_MENU) },
  multipartEnabled(): boolean { return enabled(process.env.DEBUG_MULTIPART) },
  menu(...args: any[]) { if (this.menuEnabled()) console.info('[DEBUG][menu]', ...args) },
  multipart(...args: any[]) { if (this.multipartEnabled()) console.info('[DEBUG][multipart]', ...args) },
}
