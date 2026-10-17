'use strict';

// Illustrative training catalog for the KCN Connect project.
const careers = [
  {
    "id": "career-electric",
    "category": "technical",
    "number": "01",
    "title": "Điện công nghiệp",
    "icon": "bolt",
    "description": "Làm quen hệ thống điện và thiết bị trong môi trường sản xuất, xây dựng nền tảng để học các kỹ năng kỹ thuật chuyên sâu.",
    "skills": [
      "Nhận biết thiết bị, ký hiệu điện cơ bản",
      "Đọc hiểu sơ đồ đơn giản trên mô hình",
      "Tác phong và nguyên tắc an toàn kỹ thuật"
    ],
    "audience": "Người yêu thích kỹ thuật, muốn tìm hiểu công việc liên quan đến điện tại nhà máy.",
    "context": "Các vị trí hỗ trợ kỹ thuật, lắp đặt hoặc bảo trì tùy năng lực và yêu cầu của từng doanh nghiệp.",
    "course": "electric"
  },
  {
    "id": "career-mechanical",
    "category": "technical",
    "number": "02",
    "title": "Cơ khí & lắp ráp",
    "icon": "tool",
    "description": "Tìm hiểu dụng cụ, chi tiết và quy trình lắp ráp; rèn sự chính xác và khả năng làm việc theo hướng dẫn kỹ thuật.",
    "skills": [
      "Nhận biết dụng cụ và chi tiết cơ bản",
      "Làm quen bản vẽ, trình tự lắp ráp",
      "Kiểm tra và giữ ngăn nắp vị trí làm việc"
    ],
    "audience": "Người thích công việc thực hành, quan tâm đến chi tiết máy và lắp ráp trong sản xuất.",
    "context": "Công việc lắp ráp, hỗ trợ gia công và hoàn thiện sản phẩm theo quy trình của nhà máy.",
    "course": "safety"
  },
  {
    "id": "career-welding",
    "category": "technical",
    "number": "03",
    "title": "Kỹ thuật hàn cơ bản",
    "icon": "shield",
    "description": "Khám phá kiến thức nền tảng của nghề hàn, yêu cầu an toàn và định hướng thực hành có người hướng dẫn.",
    "skills": [
      "Nhận biết vật liệu và thiết bị học nghề",
      "Hiểu yêu cầu bảo hộ, an toàn khu vực hàn",
      "Làm quen tiêu chí quan sát mối hàn mẫu"
    ],
    "audience": "Người muốn tìm hiểu lĩnh vực kết cấu kim loại và công việc gia công trong nhà máy.",
    "context": "Định hướng các công việc gia công, kết cấu và lắp ghép kim loại sau khi được đào tạo phù hợp.",
    "course": "safety"
  },
  {
    "id": "career-production",
    "category": "production",
    "number": "04",
    "title": "Vận hành & lắp ráp sản xuất",
    "icon": "case",
    "description": "Chuẩn bị tác phong công nghiệp, kỹ năng phối hợp và khả năng tuân thủ hướng dẫn tại dây chuyền sản xuất.",
    "skills": [
      "Làm quen quy trình và hướng dẫn công đoạn",
      "Phối hợp trong nhóm, bàn giao công việc",
      "Ghi nhận bất thường và báo cáo đúng cách"
    ],
    "audience": "Người mới bắt đầu, muốn xây dựng nền tảng trước khi tham gia môi trường sản xuất.",
    "context": "Các công đoạn sản xuất, lắp ráp và hỗ trợ dây chuyền theo hướng dẫn cụ thể của doanh nghiệp.",
    "course": "safety"
  },
  {
    "id": "career-sewing",
    "category": "production",
    "number": "05",
    "title": "May công nghiệp",
    "icon": "book",
    "description": "Tìm hiểu thao tác nền tảng, cách đọc yêu cầu sản phẩm và tác phong làm việc trong lĩnh vực may mặc.",
    "skills": [
      "Nhận biết vật liệu, dụng cụ và công đoạn",
      "Làm quen tiêu chí đường may, sản phẩm mẫu",
      "Rèn sự tỉ mỉ và phối hợp theo chuyền"
    ],
    "audience": "Người quan tâm công việc may mặc, thích thực hành và có sự kiên trì với chi tiết.",
    "context": "Các công đoạn may, hoàn thiện và kiểm tra sản phẩm tùy chương trình đào tạo và yêu cầu của nhà máy.",
    "course": "quality"
  },
  {
    "id": "career-quality",
    "category": "quality",
    "number": "06",
    "title": "Kiểm tra chất lượng sản phẩm",
    "icon": "search",
    "description": "Rèn kỹ năng quan sát, đo kiểm cơ bản và ghi nhận kết quả để hỗ trợ chất lượng trong quá trình sản xuất.",
    "skills": [
      "Nhận biết tiêu chí và lỗi sản phẩm mẫu",
      "Làm quen dụng cụ đo trên mẫu thực hành",
      "Ghi nhận, phân loại và trình bày kết quả"
    ],
    "audience": "Người cẩn thận, yêu thích công việc quan sát và làm việc theo tiêu chí rõ ràng.",
    "context": "Định hướng công việc kiểm tra sản phẩm và hỗ trợ kiểm soát chất lượng trong nhà máy.",
    "course": "quality"
  }
];
const courses = [
  {
    "id": "safety",
    "num": "01",
    "color": "",
    "category": "Nền tảng cho người lao động",
    "title": "An toàn trong môi trường sản xuất",
    "url": "khoa-hoc-an-toan-san-xuat.html",
    "description": "Làm quen quy tắc an toàn, tác phong tại xưởng và cách nhận biết nguy cơ thường gặp trước khi bước vào môi trường sản xuất.",
    "duration": "4 buổi dự kiến",
    "level": "NỀN TẢNG",
    "audience": [
      "Người mới chuẩn bị tham gia môi trường nhà máy, xưởng sản xuất.",
      "Người lao động muốn củng cố tác phong và kiến thức an toàn nền tảng.",
      "Người chuẩn bị học các kỹ năng nghề có nội dung thực hành."
    ],
    "outcomes": [
      "Nhận biết một số nhóm nguy cơ thường gặp trong tình huống học tập mô phỏng.",
      "Hiểu vai trò của trang bị bảo hộ và việc tuân thủ hướng dẫn tại xưởng.",
      "Biết cách ghi nhận, thông báo bất thường và phối hợp với người phụ trách.",
      "Hình thành thói quen giữ nơi làm việc ngăn nắp, đúng quy trình."
    ],
    "modules": [
      {
        "title": "Làm quen môi trường sản xuất",
        "duration": "Buổi 1",
        "goal": "Hiểu tác phong công nghiệp và cách tiếp cận một khu vực làm việc mới.",
        "topics": [
          "Nội quy, biển báo và các khu vực cần chú ý.",
          "Nhận diện nguy cơ qua hình ảnh và tình huống mô phỏng."
        ]
      },
      {
        "title": "Bảo hộ và tổ chức vị trí làm việc",
        "duration": "Buổi 2",
        "goal": "Hiểu yêu cầu sử dụng bảo hộ theo hướng dẫn của người phụ trách.",
        "topics": [
          "Vai trò của các nhóm trang bị bảo hộ trong sản xuất.",
          "Sắp xếp dụng cụ, vệ sinh và nền tảng 5S tại vị trí học tập."
        ]
      },
      {
        "title": "Phối hợp và báo cáo bất thường",
        "duration": "Buổi 3",
        "goal": "Biết thông báo cho người phụ trách khi gặp tình huống chưa rõ.",
        "topics": [
          "Thông tin cần ghi nhận khi phát hiện nguy cơ hoặc bất thường.",
          "Giao tiếp khi bàn giao công đoạn và phối hợp theo nhóm."
        ]
      },
      {
        "title": "Thực hành tình huống và phản hồi",
        "duration": "Buổi 4",
        "goal": "Vận dụng kiến thức trong tình huống được giảng viên kiểm soát.",
        "topics": [
          "Bài tập quan sát, lựa chọn cách xử lý và trình bày lý do.",
          "Nhận phản hồi, tổng hợp các thói quen cần duy trì."
        ]
      }
    ],
    "method": "Kết hợp trao đổi ngắn, quan sát hình ảnh, thảo luận nhóm và tình huống mô phỏng. Mọi hoạt động thực hành diễn ra theo hướng dẫn, không tự ý thao tác với thiết bị thực tế.",
    "assessment": "Đánh giá qua bài tập nhận diện tình huống, mức độ tham gia và phản hồi của người hướng dẫn. Điều kiện hoàn thành cụ thể được trao đổi trước khi đăng ký lớp.",
    "preparation": "Không yêu cầu kinh nghiệm nghề trước đó. Hãy chia sẻ công việc hoặc ngành nghề bạn quan tâm để tư vấn viên đề xuất hướng học tiếp theo."
  },
  {
    "id": "electric",
    "num": "02",
    "color": "copper",
    "category": "Kỹ năng nghề kỹ thuật",
    "title": "Điện công nghiệp nền tảng",
    "url": "khoa-hoc-dien-cong-nghiep.html",
    "description": "Tiếp cận kiến thức thiết bị, mạch điện và sơ đồ cơ bản; rèn cách quan sát, thực hành trên mô hình dưới sự hướng dẫn.",
    "duration": "12 buổi dự kiến",
    "level": "KỸ NĂNG NGHỀ",
    "audience": [
      "Người muốn bắt đầu tìm hiểu lĩnh vực điện trong môi trường sản xuất.",
      "Người lao động cần bổ sung nền tảng trước khi học nội dung kỹ thuật chuyên sâu.",
      "Người quan tâm công việc hỗ trợ lắp đặt, kỹ thuật hoặc bảo trì."
    ],
    "outcomes": [
      "Nhận biết một số thiết bị, ký hiệu và chức năng điện cơ bản.",
      "Đọc được các thành phần chính của sơ đồ đơn giản trên mô hình học tập.",
      "Biết chuẩn bị và ghi chép nội dung thực hành theo yêu cầu của người hướng dẫn.",
      "Hiểu giới hạn kỹ năng hiện có và các nội dung cần tiếp tục rèn luyện."
    ],
    "modules": [
      {
        "title": "Định hướng nghề và nguyên tắc an toàn",
        "duration": "Buổi 1–2",
        "goal": "Làm rõ phạm vi học tập và yêu cầu tuân thủ an toàn trong thực hành điện.",
        "topics": [
          "Môi trường làm việc và vai trò của nhân sự kỹ thuật điện.",
          "Nội quy, bảo hộ và việc thực hành trên mô hình có giám sát."
        ]
      },
      {
        "title": "Nhận biết thiết bị và ký hiệu",
        "duration": "Buổi 3–4",
        "goal": "Xây dựng vốn từ và khả năng quan sát thiết bị nền tảng.",
        "topics": [
          "Nhóm thiết bị đóng cắt, bảo vệ và điều khiển ở mức giới thiệu.",
          "Đối chiếu thiết bị mẫu với ký hiệu trên tài liệu học tập."
        ]
      },
      {
        "title": "Đọc hiểu sơ đồ đơn giản",
        "duration": "Buổi 5–6",
        "goal": "Làm quen cách theo dõi các thành phần và mối liên hệ trong sơ đồ.",
        "topics": [
          "Cấu trúc tài liệu và trình tự đọc một sơ đồ mẫu.",
          "Bài tập nhận diện thành phần, ghi chú và trao đổi với giảng viên."
        ]
      },
      {
        "title": "Thực hành trên mô hình đào tạo",
        "duration": "Buổi 7–8",
        "goal": "Rèn thao tác theo bài tập được người hướng dẫn phê duyệt.",
        "topics": [
          "Chuẩn bị mô hình và dụng cụ theo phiếu thực hành.",
          "Quan sát, thực hiện phần việc được phân công và ghi lại kết quả."
        ]
      },
      {
        "title": "Quan sát bất thường và ghi nhận",
        "duration": "Buổi 9–10",
        "goal": "Rèn cách mô tả hiện tượng và trao đổi vấn đề kỹ thuật cơ bản.",
        "topics": [
          "Tình huống mô phỏng để nhận biết thông tin còn thiếu.",
          "Lập phiếu ghi nhận và trao đổi hướng kiểm tra với người hướng dẫn."
        ]
      },
      {
        "title": "Bài thực hành tổng hợp",
        "duration": "Buổi 11–12",
        "goal": "Tổng hợp kiến thức và xác định lộ trình rèn luyện tiếp theo.",
        "topics": [
          "Hoàn thành bài tập mô hình theo tiêu chí được thông báo.",
          "Nhận phản hồi về thao tác, ghi chép và nguyên tắc an toàn."
        ]
      }
    ],
    "method": "Học kiến thức gắn với mẫu thiết bị, tài liệu sơ đồ và mô hình thực hành. Giảng viên hướng dẫn, theo dõi và phản hồi theo từng bài; người học không tự thao tác với hệ thống điện đang vận hành tại nhà máy.",
    "assessment": "Đánh giá quá trình học, cách đọc tài liệu, kết quả bài tập mô hình và ý thức tuân thủ hướng dẫn. Nội dung học là nền tảng, không thay thế yêu cầu đào tạo hoặc phân quyền chuyên môn tại doanh nghiệp.",
    "preparation": "Trao đổi với tư vấn viên về nền tảng hiện tại, mục tiêu và thời gian có thể học. Tài liệu, dụng cụ và điều kiện tham gia được xác nhận theo từng lớp."
  },
  {
    "id": "quality",
    "num": "03",
    "color": "blue",
    "category": "Kỹ năng phục vụ sản xuất",
    "title": "Kiểm tra chất lượng cơ bản",
    "url": "khoa-hoc-kiem-tra-chat-luong.html",
    "description": "Rèn tính chính xác, làm quen dụng cụ đo và cách ghi nhận kết quả kiểm tra sản phẩm trong bối cảnh sản xuất.",
    "duration": "8 buổi dự kiến",
    "level": "THỰC HÀNH",
    "audience": [
      "Người quan tâm lĩnh vực kiểm tra sản phẩm và kiểm soát chất lượng.",
      "Người mới muốn rèn kỹ năng quan sát, ghi chép và tuân thủ tiêu chí.",
      "Người lao động muốn bổ sung nền tảng để trao đổi với bộ phận chất lượng."
    ],
    "outcomes": [
      "Phân biệt yêu cầu, tiêu chí kiểm tra và lỗi trên sản phẩm mẫu.",
      "Làm quen việc sử dụng dụng cụ đo cơ bản theo hướng dẫn.",
      "Ghi nhận kết quả kiểm tra rõ ràng, có thông tin truy vết cần thiết.",
      "Trình bày bất thường và phối hợp trao đổi theo quy trình mẫu."
    ],
    "modules": [
      {
        "title": "Chất lượng trong quá trình sản xuất",
        "duration": "Buổi 1–2",
        "goal": "Hiểu vì sao mỗi công đoạn cần tiêu chí kiểm tra rõ ràng.",
        "topics": [
          "Vai trò của kiểm tra sản phẩm và cách đọc yêu cầu mẫu.",
          "Phân biệt quan sát thực tế với kết luận chưa đủ căn cứ."
        ]
      },
      {
        "title": "Quan sát và nhận diện lỗi mẫu",
        "duration": "Buổi 3–4",
        "goal": "Rèn khả năng so sánh sản phẩm với tiêu chí được cung cấp.",
        "topics": [
          "Bài tập nhận diện, mô tả và phân loại lỗi trên mẫu học tập.",
          "Cách trao đổi khi tiêu chí hoặc tình trạng sản phẩm chưa rõ."
        ]
      },
      {
        "title": "Làm quen đo kiểm và ghi nhận",
        "duration": "Buổi 5–6",
        "goal": "Tiếp cận dụng cụ đo cơ bản và cách lưu kết quả có cấu trúc.",
        "topics": [
          "Thực hành trên mẫu theo chỉ dẫn của giảng viên.",
          "Thông tin cần ghi trong phiếu kiểm tra và cách kiểm tra lại ghi chép."
        ]
      },
      {
        "title": "Bài tập kiểm tra tổng hợp",
        "duration": "Buổi 7–8",
        "goal": "Thực hiện một nhiệm vụ kiểm tra mẫu từ yêu cầu tới báo cáo.",
        "topics": [
          "Quan sát, ghi nhận và tổng hợp kết quả theo biểu mẫu.",
          "Trình bày nhận xét, nhận phản hồi và xác định kỹ năng cần bổ sung."
        ]
      }
    ],
    "method": "Kết hợp sản phẩm mẫu, phiếu kiểm tra, thảo luận tình huống và bài tập có hướng dẫn. Người học được phản hồi về sự chính xác, cách ghi chép và khả năng làm việc theo tiêu chí.",
    "assessment": "Đánh giá qua bài tập nhận diện lỗi, phiếu ghi kết quả và phần trình bày cuối chương trình. Tiêu chí hoàn thành được xác nhận trước khi học, theo nội dung cụ thể của lớp.",
    "preparation": "Không cần mặc định đã có kinh nghiệm QC. Tư vấn viên sẽ trao đổi nền tảng, ngành sản xuất bạn quan tâm và mục tiêu để đề xuất lớp phù hợp."
  }
];
const courseDrawings = {
  safety: '<path d="M48 94v-9a42 42 0 0 1 84 0v9M42 94h96v12H42zM79 46v39M101 46v39M57 71l9 7m57-7-9 7"/><path d="M56 118h68M68 128h44"/><circle cx="145" cy="44" r="19"/><path d="m136 44 6 6 11-13"/>',
  electric: '<rect x="48" y="27" width="88" height="104" rx="7"/><rect x="60" y="40" width="64" height="27" rx="3"/><path d="m96 44-13 15h12l-7 14M63 83h14m30 0h14M63 95h14m30 0h14M63 108h14m30 0h14M92 84v26M72 131v12m42-12v12M31 40v73h17M136 51h17v62h-17"/><circle cx="153" cy="34" r="8"/><path d="M153 42v9"/>',
  quality: '<path d="M40 50h103v21H40zM52 50v-9m11 9v-6m11 6v-9m11 9v-6m11 6v-9m11 9v-6m11 6v-9m11 9v-6M52 71v42h12V71M114 71v42h12V71M64 100h50M86 55v11"/><circle cx="141" cy="111" r="22"/><path d="m130 111 7 7 15-16M45 130h70"/>'
};

const icon = name => '<svg class="icon" aria-hidden="true"><use href="#i-' + name + '"/></svg>';
const escapeHtml = value => String(value).replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
const normalize = value => value.toLocaleLowerCase('vi').normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/đ/g,'d');
const query = selector => document.querySelector(selector);
const queryAll = selector => Array.from(document.querySelectorAll(selector));
const validInterests = new Set(['not-sure', ...courses.map(course=>course.id), ...careers.map(career=>career.id)]);
const validContactTimes = new Set(['any', 'morning', 'afternoon']);

function consultationUrl(_kind, selection) {
  const params = new URLSearchParams({'nhu-cau':'training'});
  if(validInterests.has(selection))params.set('quan-tam',selection);
  return 'lien-he.html?'+params.toString();
}
function updateConsultationUrl(selection) {
  try {
    const url = new URL(window.location.href);
    url.searchParams.set('nhu-cau','training');
    if(validInterests.has(selection))url.searchParams.set('quan-tam',selection);else url.searchParams.delete('quan-tam');
    window.history.replaceState(null,'',url);
  } catch { /* Local-file viewing does not require History API support. */ }
}
function selectConsultation(_kind, selection, moveToForm=true) {
  if(!query('#consultation-form')) { window.location.assign(consultationUrl('training',selection));return false; }
  query('#interest').innerHTML='<option value="not-sure">Tôi cần tư vấn ngành nghề và lộ trình học</option><optgroup label="Khóa học">'+courses.map(course=>'<option value="'+course.id+'">'+escapeHtml(course.title)+'</option>').join('')+'</optgroup><optgroup label="Ngành nghề quan tâm">'+careers.map(career=>'<option value="'+career.id+'">'+escapeHtml(career.title)+'</option>').join('')+'</optgroup>';
  query('#interest').value=validInterests.has(selection)?selection:'not-sure';
  query('#form-error').hidden=true;
  updateConsultationUrl(query('#interest').value);
  if(moveToForm)query('#lien-he').scrollIntoView({behavior:window.matchMedia('(prefers-reduced-motion: reduce)').matches?'auto':'smooth',block:'start'});
  return true;
}

let activeCareerFilter='all';
function renderCareers() {
  if(!query('#careers-grid'))return;
  const term=normalize(query('#career-keyword').value.trim());
  const visible=careers.filter(career=>(activeCareerFilter==='all'||career.category===activeCareerFilter)&&normalize(career.title+' '+career.description+' '+career.skills.join(' ')).includes(term));
  query('#careers-grid').innerHTML=visible.map(career=>'<article class="career-card"><div class="career-card-top"><span class="career-icon">'+icon(career.icon)+'</span><span>NGÀNH '+career.number+'</span></div><h3>'+escapeHtml(career.title)+'</h3><p>'+escapeHtml(career.description)+'</p><ul>'+career.skills.map(skill=>'<li>'+icon('check')+escapeHtml(skill)+'</li>').join('')+'</ul><div class="career-card-bottom"><button class="text-link" type="button" data-career-detail="'+career.id+'">Tìm hiểu ngành '+icon('arrow')+'</button><a href="'+consultationUrl('training',career.id)+'" aria-label="Nhận tư vấn ngành '+escapeHtml(career.title)+'">'+icon('up')+'</a></div></article>').join('');
  query('#career-empty').hidden=visible.length>0;
  query('#career-count').textContent=visible.length+' ngành nghề tham khảo';
  queryAll('[data-career-filter]').forEach(button=>{const active=button.dataset.careerFilter===activeCareerFilter;button.classList.toggle('active',active);button.setAttribute('aria-pressed',String(active));});
}
function renderCourses() {
  if(!query('#courses-grid'))return;
  query('#courses-grid').innerHTML=courses.map(course=>'<article class="course-card"><div class="course-cover '+course.color+'"><span class="course-number">KHÓA '+course.num+'</span><svg viewBox="0 0 185 160" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">'+courseDrawings[course.id]+'</svg><span class="course-level">'+course.level+'</span></div><div class="course-body"><span class="course-category">'+escapeHtml(course.category)+'</span><h3>'+escapeHtml(course.title)+'</h3><p>'+escapeHtml(course.description)+'</p><div class="course-meta"><span>'+icon('clock')+course.duration+'</span><span>'+icon('users')+'Có hướng dẫn thực hành</span></div><div class="course-bottom"><span>Chương trình minh họa</span><a class="text-link" href="'+course.url+'">Tìm hiểu khóa học '+icon('arrow')+'</a></div></div></article>').join('');
}

const dialog=query('#detail-dialog');
let dialogOpener=null;
function openDialog(html){dialogOpener=document.activeElement;query('#dialog-body').innerHTML=html;if(!dialog.open)dialog.showModal();document.body.classList.add('modal-open');}
function closeDialog(){dialog.close();}
dialog.addEventListener('close',()=>{document.body.classList.remove('modal-open');if(dialogOpener?.isConnected)dialogOpener.focus({preventScroll:true});});
query('.dialog-close').addEventListener('click',closeDialog);
dialog.addEventListener('click',event=>{if(event.target===dialog)closeDialog();});
function showCareer(career){
  const course=courses.find(item=>item.id===career.course);
  openDialog('<div class="dialog-body"><p class="dialog-eyebrow">Định hướng ngành nghề đào tạo ngắn hạn</p><h2 class="dialog-title" id="dialog-title">'+escapeHtml(career.title)+'</h2><p class="dialog-lead">'+escapeHtml(career.description)+'</p><h4>Phù hợp với ai?</h4><p class="dialog-lead">'+escapeHtml(career.audience)+'</p><h4>Kỹ năng nên chuẩn bị</h4><ul>'+career.skills.map(skill=>'<li>'+escapeHtml(skill)+'</li>').join('')+'</ul><h4>Ứng dụng trong môi trường sản xuất</h4><p class="dialog-lead">'+escapeHtml(career.context)+'</p><h4>Bắt đầu từ nền tảng</h4><p class="dialog-lead">Bạn có thể tìm hiểu <a class="inline-button" href="'+course.url+'">'+escapeHtml(course.title)+'</a> và trao đổi để chọn nội dung chuyên môn phù hợp.</p><button class="button" type="button" data-consult="training" data-interest="'+career.id+'">Nhận tư vấn ngành nghề '+icon('arrow')+'</button><p class="dialog-disclaimer">Nội dung định hướng minh họa. Lộ trình, lớp học và yêu cầu tham gia được tư vấn trước khi đăng ký.</p></div>');
}
document.addEventListener('click',event=>{
  const detail=event.target.closest('[data-career-detail]');
  if(detail){const career=careers.find(item=>item.id===detail.dataset.careerDetail);if(career)showCareer(career);}
  const consult=event.target.closest('[data-consult]');
  if(consult){if(dialog.open)closeDialog();selectConsultation('training',consult.dataset.interest);}
  const filter=event.target.closest('[data-career-filter]');
  if(filter){activeCareerFilter=filter.dataset.careerFilter;query('#career-category').value=activeCareerFilter;renderCareers();}
  if(event.target.closest('[data-dismiss-dialog]'))closeDialog();
});
query('#career-search')?.addEventListener('submit',event=>{event.preventDefault();activeCareerFilter=query('#career-category').value;renderCareers();});
query('#career-category')?.addEventListener('change',()=>{activeCareerFilter=query('#career-category').value;renderCareers();});
query('#career-keyword')?.addEventListener('input',renderCareers);
const menuButton=query('.menu-toggle');
function setMenu(open){menuButton.setAttribute('aria-expanded',String(open));menuButton.setAttribute('aria-label',open?'Đóng menu':'Mở menu');query('#main-nav').classList.toggle('is-open',open);}
menuButton.addEventListener('click',()=>setMenu(menuButton.getAttribute('aria-expanded')!=='true'));
queryAll('#main-nav a').forEach(link=>link.addEventListener('click',()=>setMenu(false)));
document.addEventListener('keydown',event=>{if(event.key==='Escape')setMenu(false);});
query('#privacy-button')?.addEventListener('click',()=>openDialog('<div class="dialog-body"><p class="dialog-eyebrow">Đăng ký tư vấn học nghề</p><h2 class="dialog-title" id="dialog-title">Thông tin sử dụng dữ liệu</h2><p class="dialog-lead">Khi bạn đồng ý và gửi đăng ký, họ tên, số điện thoại, email (nếu có), ngành nghề hoặc khóa học quan tâm, thời gian liên hệ và nội dung chia sẻ được gửi tới máy chủ và lưu trong hệ thống KCN Connect để tiếp nhận, liên hệ tư vấn.</p><h4>Xác nhận đăng ký</h4><p class="dialog-lead">Sau khi hệ thống lưu thành công, bạn sẽ nhận được mã đăng ký. Việc đóng tab trình duyệt không xóa thông tin đã gửi. Nếu cần trao đổi hoặc yêu cầu cập nhật thông tin, hãy liên hệ và cung cấp mã đăng ký.</p><h4>Liên hệ trực tiếp</h4><p class="dialog-lead">Điện thoại: <a href="tel:0368488333">0368488333</a><br>Email: <a href="mailto:contact@csgroup.vn">contact@csgroup.vn</a></p><button class="button" type="button" data-dismiss-dialog>Đã hiểu '+icon('check')+'</button></div>'));

function consultationError(response, data) {
  let message;
  if(response.status===401)message='Phiên kết nối không hợp lệ. Vui lòng tải lại trang rồi gửi đăng ký.';
  else if(response.status===403)message='Phiên bảo mật đã hết hạn hoặc yêu cầu bị từ chối. Vui lòng tải lại trang rồi gửi đăng ký.';
  else if(response.status>=500)message='Máy chủ chưa thể tiếp nhận đăng ký. Vui lòng thử lại sau.';
  else message=typeof data?.message==='string'?data.message:'Không thể gửi đăng ký. Vui lòng kiểm tra thông tin và thử lại.';
  const error=new Error(message);
  error.fields=data?.fields;
  return error;
}

let consultationSubmitting=false;
query('#consultation-form')?.addEventListener('submit',async event=>{
  event.preventDefault();
  if(consultationSubmitting)return;
  const form=event.currentTarget;
  const name=query('#full-name'),phone=query('#phone'),email=query('#email'),consent=query('#consent'),interest=query('#interest'),time=query('#contact-time'),message=query('#message');
  let firstInvalid=null;const errors=[];
  const fields={fullName:name,phone,email,consent,programName:interest,program_name:interest,contactTime:time,description:message};
  Object.values(fields).forEach(input=>input.removeAttribute('aria-invalid'));
  const invalid=(input,message)=>{input.setAttribute('aria-invalid','true');errors.push(message);if(!firstInvalid)firstInvalid=input;};
  const fullName=name.value.trim(),emailAddress=email.value.trim(),details=message.value.trim(),programName=interest.selectedOptions[0]?.textContent.trim()||'';
  if(fullName.length<2||fullName.length>120)invalid(name,'Vui lòng nhập họ tên từ 2 đến 120 ký tự.');
  const normalizedPhone=phone.value.replace(/[\s().-]/g,'');
  if(normalizedPhone.length>20||!/^(?:0\d{9}|\+?84\d{9})$/.test(normalizedPhone))invalid(phone,'Vui lòng nhập số điện thoại hợp lệ, ví dụ 0912345678.');
  if(emailAddress.length>190||(emailAddress&&!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailAddress)))invalid(email,'Vui lòng nhập email hợp lệ, tối đa 190 ký tự.');
  if(!validInterests.has(interest.value)||!programName||programName.length>255)invalid(interest,'Vui lòng chọn ngành nghề hoặc khóa học bạn quan tâm, tối đa 255 ký tự.');
  if(!validContactTimes.has(time.value))invalid(time,'Vui lòng chọn thời gian liên hệ phù hợp.');
  if(details.length>2000)invalid(message,'Nội dung chia sẻ tối đa 2000 ký tự.');
  if(!consent.checked)invalid(consent,'Vui lòng đọc và đồng ý với việc sử dụng thông tin để liên hệ tư vấn.');
  if(errors.length){query('#form-error').textContent=errors[0];query('#form-error').hidden=false;firstInvalid.focus();return;}
  query('#form-error').hidden=true;
  const record={fullName,phone:normalizedPhone,email:emailAddress,contactTime:time.value,description:details,program_name:programName};
  const submit=form.querySelector('[type="submit"]'),submitLabel=submit.innerHTML;
  consultationSubmitting=true;submit.disabled=true;submit.textContent='Đang gửi đăng ký…';form.setAttribute('aria-busy','true');
  let requestSent=false;
  try {
    const csrfResponse=await fetch('/api/auth/csrf',{credentials:'same-origin',headers:{Accept:'application/json'},cache:'no-store'});
    const csrf=await csrfResponse.json().catch(()=>null);
    if(!csrfResponse.ok)throw consultationError(csrfResponse,csrf);
    if(!csrf||typeof csrf.token!=='string'||!csrf.token||typeof csrf.headerName!=='string'||!csrf.headerName)throw new Error('Không lấy được phiên bảo mật. Vui lòng tải lại trang và thử lại.');
    requestSent=true;
    const response=await fetch('/api/public/consultations',{method:'POST',credentials:'same-origin',headers:{Accept:'application/json','Content-Type':'application/json',[csrf.headerName]:csrf.token},body:JSON.stringify(record)});
    const receipt=await response.json().catch(()=>null);
    if(response.status!==201)throw consultationError(response,receipt);
    if(!receipt||receipt.id==null||typeof receipt.fullName!=='string')throw new Error('Máy chủ đã phản hồi nhưng thiếu mã xác nhận. Vui lòng liên hệ trung tâm để kiểm tra trước khi gửi lại.');
    openDialog('<div class="dialog-body"><div class="success-icon">'+icon('check')+'</div><p class="dialog-eyebrow">Đăng ký tư vấn thành công</p><h2 class="dialog-title" id="dialog-title">Cảm ơn bạn, '+escapeHtml(receipt.fullName)+'!</h2><p class="dialog-lead">Thông tin đăng ký tư vấn của bạn đã được lưu trong hệ thống. Trung tâm sẽ sử dụng thông tin bạn đã cung cấp để liên hệ tư vấn.</p><p class="success-ref">Mã đăng ký: '+escapeHtml(receipt.id)+'</p><button class="button" type="button" data-dismiss-dialog>Tiếp tục khám phá '+icon('arrow')+'</button></div>');
    form.reset();selectConsultation('training',undefined,false);
  } catch(error) {
    const fieldMessages=[];
    if(error.fields&&typeof error.fields==='object')Object.entries(error.fields).forEach(([key,value])=>{
      if(typeof value!=='string')return;
      fieldMessages.push(value);
      if(fields[key]){fields[key].setAttribute('aria-invalid','true');if(!firstInvalid)firstInvalid=fields[key];}
    });
    const networkMessage=requestSent?'Chưa xác nhận được kết quả gửi đăng ký do mất kết nối. Vui lòng kiểm tra kết nối hoặc liên hệ trung tâm trước khi gửi lại.':'Không kết nối được máy chủ. Vui lòng kiểm tra kết nối và thử lại.';
    query('#form-error').textContent=fieldMessages.length?fieldMessages.join(' '):(error instanceof TypeError?networkMessage:error.message||'Không thể gửi đăng ký. Vui lòng thử lại.');
    query('#form-error').hidden=false;
    firstInvalid?.focus();
  } finally {
    consultationSubmitting=false;submit.disabled=false;submit.innerHTML=submitLabel;form.removeAttribute('aria-busy');
  }
});
renderCareers();renderCourses();
if(query('#consultation-form')){
  const params=new URLSearchParams(window.location.search);
  const legacy={assembly:'career-production',maintenance:'electric'};
  const selected=params.get('quan-tam');
  selectConsultation('training',Object.hasOwn(legacy,selected)?legacy[selected]:selected,false);
  query('#interest').addEventListener('change',()=>updateConsultationUrl(query('#interest').value));
}
const legacyPages={'#trang-chu':'index.html','#gioi-thieu':'ve-chung-toi.html','#viec-lam':'nganh-nghe.html','#dao-tao':'dao-tao.html','#lo-trinh':'lo-trinh.html','#doanh-nghiep':'doanh-nghiep.html','#lien-he':'lien-he.html'};
if(document.body.dataset.page==='home'&&Object.hasOwn(legacyPages,window.location.hash))window.location.replace(legacyPages[window.location.hash]);
