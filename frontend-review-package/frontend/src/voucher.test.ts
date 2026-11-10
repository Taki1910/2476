import {describe,expect,it} from 'vitest'
import {ApiError} from './api'
import {setLocale} from './i18n'
import {voucherError} from './voucher'

describe('customer-safe voucher messages',()=>{
  it('maps stable codes without exposing backend detail in VI and EN',()=>{
    for(const locale of ['en','vi-VN'] as const){setLocale(locale);const message=voucherError(new ApiError(409,'VOUCHER_STALE','fingerprint mismatch revision 7'));expect(message).not.toContain('fingerprint');expect(message).not.toContain('revision 7')}
  })
  it('does not expose unknown server detail',()=>expect(voucherError(new ApiError(500,'SQL_ERROR','SELECT normalized_code'))).not.toContain('normalized_code'))
})
