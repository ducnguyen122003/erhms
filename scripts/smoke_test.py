#!/usr/bin/env python3
"""Smoke test tạo một nhu cầu mẫu, duyệt và đóng. Chạy sau khi stack sẵn sàng."""
import argparse, datetime, http.cookiejar, json, os, urllib.request, urllib.parse
class Client:
    def __init__(self, base):
        self.base=base.rstrip('/')
        self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.csrf=None
    def request(self, path, method='GET', data=None, form=False):
        headers={}
        body=None
        if data is not None:
            body=(urllib.parse.urlencode(data) if form else json.dumps(data)).encode()
            headers['Content-Type']='application/x-www-form-urlencoded' if form else 'application/json'
        if method!='GET':headers[self.csrf['headerName']]=self.csrf['token']
        with self.opener.open(urllib.request.Request(self.base+path,data=body,headers=headers,method=method),timeout=15) as response:
            content=response.read()
            return json.loads(content) if content else None
    def login(self, email, password):
        self.csrf=self.request('/auth/csrf')
        self.request('/auth/login','POST',{'username':email,'password':password},form=True)
        self.csrf=self.request('/auth/csrf')
        return self.request('/auth/me')
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--base-url',default='http://localhost:8080/api');args=parser.parse_args()
    password=os.environ.get('DEMO_PASSWORD','KcnDemo@2026!')
    hr=Client(args.base_url);manager=Client(args.base_url)
    assert hr.login('hr@kcn.local',password)['role']=='EMPLOYER_HR'
    assert manager.login('manager@kcn.local',password)['role']=='SERVICE_MANAGER'
    record=hr.request('/recruitment-requests','POST',{'title':'[SMOKE] Công nhân vận hành máy','quantity':5,'shift':'DAY','skills':'Thao tác cơ bản','location':'Lô A1 - dữ liệu kiểm tra','neededBy':(datetime.date.today()+datetime.timedelta(days=30)).isoformat(),'version':0})
    path='/recruitment-requests/'+record['id']
    assert record['status']=='DRAFT'
    record=hr.request(path+'/submit','POST',{'version':record['version']})
    assert record['status']=='SUBMITTED'
    record=manager.request(path+'/approve','POST',{'version':record['version']})
    assert record['status']=='APPROVED'
    record=manager.request(path+'/close','POST',{'version':record['version']})
    assert record['status']=='CLOSED'
    assert len(hr.request(path+'/history'))==4
    assert hr.request('/recruitment-requests?q=%5BSMOKE%5D')['total']>=1
    hr.request('/auth/logout','POST');manager.request('/auth/logout','POST')
    print('PASS: login, create, submit, approve, close, history, search, logout.')
    print('Bản ghi kiểm tra đã đóng: '+record['id'])
if __name__=='__main__':main()
