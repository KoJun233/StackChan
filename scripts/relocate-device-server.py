"""Physically relocate a paired robot's origin without pairing or rotating credentials.

Default is a plan only. --apply sends one strict USB request, printing only
whitelisted status codes; the firmware quiesces transport and authenticates the
existing refresh credential against the proposed origin before atomically saving.
"""
import argparse
import json
import time
from urllib.parse import urlsplit


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port',default='COM3')
    parser.add_argument('--server-origin',required=True)
    parser.add_argument('--apply',action='store_true')
    args=parser.parse_args()
    try:
        uri=urlsplit(args.server_origin)
        valid=(uri.scheme in ('http','https') and bool(uri.hostname) and uri.username is None and uri.password is None
               and uri.path=='' and not uri.query and not uri.fragment and len(args.server_origin.encode('utf-8'))<192
               and (uri.port is None or 1<=uri.port<=65535))
    except ValueError:
        valid=False
    if not valid: parser.error('server-origin must be a bounded canonical HTTP(S) origin without credentials, path, query or fragment')
    if not args.apply:
        print(f'PLAN only: port={args.port}, server-origin={args.server_origin}; no serial connection or bytes sent')
        return
    import serial
    device=serial.Serial(port=None,baudrate=115200,timeout=0.2)
    device.dtr=False;device.rts=False;device.port=args.port
    device.open()
    try:
        device.reset_input_buffer()
        request=json.dumps({'type':'relocate_server','serverBaseUrl':args.server_origin},separators=(',',':'))+'\n'
        device.write(request.encode('utf-8'));device.flush()
        print('Physical origin relocation requested; no credentials read or printed',flush=True)
        deadline=time.monotonic()+75
        allowed={'started','complete','already_current','identity_unavailable','invalid_request','transport_busy','server_verification_failed','identity_save_failed'}
        while time.monotonic()<deadline:
            data=device.read_until(b'\n',size=512)
            if not data.endswith(b'\n'): continue
            try: value=json.loads(data.decode('utf-8'))
            except (ValueError,UnicodeError): continue
            if not isinstance(value,dict) or set(value)!={'type','status'} or value['type']!='provisioning' or not isinstance(value['status'],str) or value['status'] not in allowed: continue
            status=value['status'];print('USB relocation status: '+status,flush=True)
            if status in ('complete','already_current'): return
            if status!='started': raise SystemExit('Origin relocation failed safely; prior identity retained')
        raise SystemExit('No complete receipt within 75 seconds; inspect device state before retrying')
    finally:
        device.close()

if __name__=='__main__': main()