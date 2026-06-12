import axios from 'axios';

const http = axios.create({ baseURL: '/api/v1', timeout: 15000 });

http.interceptors.request.use((cfg) => {
  const k = localStorage.getItem('apiKey');
  if (k) cfg.headers['X-Api-Key'] = k;
  return cfg;
});

http.interceptors.response.use(
  (res) => {
    const d = res.data;
    if (d && d.code !== undefined && d.code !== 0) {
      return Promise.reject(new Error(d.message || ('code ' + d.code)));
    }
    return d ? d.data : null;
  },
  (err) => {
    if (err.response && err.response.status === 401) {
      return Promise.reject(new Error('401 未授权：请在右上角填写 X-Api-Key'));
    }
    return Promise.reject(new Error(err.response?.data?.message || err.message));
  }
);

export default http;
