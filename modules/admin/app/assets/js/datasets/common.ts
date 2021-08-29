import {DateTime} from "luxon";
import axios from "axios";


function humanFileSize(bytes: number, si: boolean = true): string {
  let thresh = si ? 1000 : 1024;
  if (Math.abs(bytes) < thresh) {
    return bytes + ' B';
  }
  let units = si
      ? ['kB', 'MB', 'GB', 'TB', 'PB', 'EB', 'ZB', 'YB']
      : ['KiB', 'MiB', 'GiB', 'TiB', 'PiB', 'EiB', 'ZiB', 'YiB'];
  let u = -1;
  do {
    bytes /= thresh;
    ++u;
  } while (Math.abs(bytes) >= thresh && u < units.length - 1);
  return `${bytes.toFixed(1)} ${units[u]}`;
}

function prettyDate(time: string): string {
  let m = DateTime.fromISO(time);
  if (m.isValid) {
    let duration = m.diffNow("minutes");
    if (duration.minutes > -1) return 'Just now';
    else return m.toRelative() as string;
  }
  return "";
}

function apiCall<T>(endpoint: {url: string, method: any}, data?: object): Promise<T> {
  return axios.request<T>({
    url: endpoint.url,
    method: endpoint.method,
    data: data,
    headers: {
      "ajax-ignore-csrf": true,
      "Content-Type": "application/json",
      "Accept": "application/json; charset=utf-8",
      "X-Requested-With": "XMLHttpRequest",
    },
    withCredentials: true,
  }).then(r => r.data);
}



export {humanFileSize, prettyDate, apiCall};
