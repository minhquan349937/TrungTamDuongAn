import { useCallback, useEffect, useMemo, useState } from "react";
import type { ReactElement } from "react";
import { Alert, App, Button, Card, Radio, Spin, Tag } from "antd";
import dayjs from "dayjs";
import { Eye, Globe, Send } from "lucide-react";
import { AlbumsEditor, ListEditor, SingleEditor, WebsiteContext } from "../features/WebsiteEditors";
import type { FieldDef } from "../features/WebsiteEditors";
import { websiteApi } from "../services/websiteApi";
import type { AdminRecord, WebsiteStatus } from "../services/websiteApi";
import { ConsultationManager } from "./ConsultationManager";
import "./website-admin.css";

const heading = (): FieldDef[] => [
  { name: "title", label: "Tiêu đề phần", type: "text", max: 200 },
  { name: "summary", label: "Mô tả ngắn", type: "textarea", max: 500, rows: 2 },
  { name: "visible", label: "Hiển thị phần này trên website", type: "switch" },
];
const body = (): FieldDef => ({ name: "body", label: "Nội dung", type: "textarea", max: 8000, rows: 6, help: "Văn bản thuần; xuống dòng để tách đoạn." });
const visible = (): FieldDef => ({ name: "visible", label: "Hiển thị trên website", type: "switch" });
const text = (r: AdminRecord, key: string) => String(r[key] ?? "");
const cell = (title: string, sub?: string) => <div><b>{title}</b>{sub && <div className="wm-sub">{sub}</div>}</div>;

function Intro() {
  return (
    <div className="wm-stack">
      <SingleEditor resource="sections" id="HERO" title="Mở đầu trang" hint="Tiêu đề lớn, mô tả, ảnh đại diện và nút kêu gọi ở đầu trang."
        fields={[
          { name: "title", label: "Tiêu đề nổi bật", type: "text", max: 200 },
          { name: "summary", label: "Mô tả ngắn", type: "textarea", max: 500, rows: 3 },
          { name: "imageMediaId", label: "Ảnh đại diện", type: "image" },
          { name: "buttonLabel", label: "Chữ trên nút đăng ký tư vấn", type: "text", max: 60 },
          visible(),
        ]} />
      <SingleEditor resource="sections" id="STORY" title="Câu chuyện và định hướng" hint="Câu chuyện thành lập, định hướng của trung tâm."
        fields={[...heading().slice(0, 2), body(), { name: "imageMediaId", label: "Ảnh minh họa", type: "image" }, visible()]} />
      <ListEditor resource="items" section="STORY" title="Giá trị cốt lõi" addLabel="Thêm giá trị" fixed={{ sectionKey: "STORY" }}
        fields={[{ name: "title", label: "Giá trị", type: "text", required: true, max: 200 }, { name: "description", label: "Mô tả", type: "textarea", max: 2000, rows: 3 }, visible()]}
        columns={[{ title: "Giá trị", render: (r) => cell(text(r, "title"), text(r, "description")) }]} />
    </div>
  );
}

function Audience() {
  return (
    <div className="wm-stack">
      <SingleEditor resource="sections" id="AUDIENCE" title="Đối tượng hỗ trợ" hint="Chỉ ghi những độ tuổi và nhóm nhu cầu trung tâm thực sự tiếp nhận."
        fields={[...heading().slice(0, 2), body(), visible()]} />
      <ListEditor resource="items" section="AUDIENCE" title="Độ tuổi và nhóm nhu cầu" addLabel="Thêm nhóm" fixed={{ sectionKey: "AUDIENCE" }}
        fields={[
          { name: "label", label: "Nhãn ngắn (ví dụ: Độ tuổi)", type: "text", max: 100 },
          { name: "title", label: "Nội dung", type: "text", required: true, max: 200 },
          { name: "description", label: "Mô tả", type: "textarea", max: 2000, rows: 3 }, visible(),
        ]}
        columns={[{ title: "Nhóm", render: (r) => cell(text(r, "title"), [text(r, "label"), text(r, "description")].filter(Boolean).join(" · ")) }]} />
    </div>
  );
}

function Services() {
  return (
    <div className="wm-stack">
      <SingleEditor resource="sections" id="SERVICES" title="Tiêu đề phần chương trình và dịch vụ" fields={heading()} />
      <ListEditor resource="services" title="Chương trình và dịch vụ" addLabel="Thêm chương trình"
        fields={[
          { name: "title", label: "Tên chương trình", type: "text", required: true, max: 200 },
          { name: "summary", label: "Mô tả ngắn", type: "textarea", max: 500, rows: 2 },
          { name: "details", label: "Nội dung chi tiết", type: "textarea", max: 6000, rows: 6 },
          { name: "audience", label: "Đối tượng phù hợp", type: "text", max: 300 },
          { name: "format", label: "Hình thức tổ chức", type: "text", max: 300 },
          { name: "imageMediaId", label: "Ảnh đại diện", type: "image" }, visible(),
        ]}
        columns={[{ title: "Chương trình", render: (r) => cell(text(r, "title"), text(r, "summary")) }, { title: "Hình thức", render: (r) => text(r, "format") || "—" }]} />
    </div>
  );
}

function Team() {
  return (
    <div className="wm-stack">
      <SingleEditor resource="sections" id="TEAM" title="Tiêu đề phần đội ngũ" fields={heading()} />
      <ListEditor resource="team" title="Đội ngũ chuyên môn" addLabel="Thêm thành viên"
        fields={[
          { name: "fullName", label: "Họ tên", type: "text", required: true, max: 150 },
          { name: "jobTitle", label: "Chức danh hoặc vai trò", type: "text", max: 150 },
          { name: "qualifications", label: "Bằng cấp", type: "textarea", max: 1000, rows: 2, help: "Chỉ ghi bằng cấp thật và được phép công khai." },
          { name: "experience", label: "Kinh nghiệm", type: "textarea", max: 1000, rows: 2 },
          { name: "bio", label: "Giới thiệu ngắn", type: "textarea", max: 1000, rows: 3 },
          { name: "photoMediaId", label: "Ảnh", type: "image" },
          { name: "publicConsent", label: "Người này đồng ý công khai thông tin trên website", type: "switch" }, visible(),
        ]}
        columns={[
          { title: "Thành viên", render: (r) => cell(text(r, "fullName"), text(r, "jobTitle")) },
          { title: "Đồng ý công khai", render: (r) => (r.publicConsent ? <Tag color="green">Đã đồng ý</Tag> : <Tag color="orange">Chưa: không hiển thị</Tag>) },
        ]} />
      <SingleEditor resource="sections" id="PROCESS" title="Tiêu đề phần quy trình đồng hành" fields={heading()} />
      <ListEditor resource="items" section="PROCESS" title="Các bước quy trình đồng hành" addLabel="Thêm bước" fixed={{ sectionKey: "PROCESS" }}
        fields={[
          { name: "label", label: "Nhãn (ví dụ: Bước 1)", type: "text", max: 100 },
          { name: "title", label: "Tên bước", type: "text", required: true, max: 200 },
          { name: "description", label: "Mô tả", type: "textarea", max: 2000, rows: 3 }, visible(),
        ]}
        columns={[{ title: "Bước", render: (r) => cell(text(r, "title"), text(r, "description")) }]} />
    </div>
  );
}

function Progress() {
  return (
    <div className="wm-stack">
      <SingleEditor resource="sections" id="PROGRESS" title="Theo dõi tiến bộ của trẻ"
        hint="Chỉ giới thiệu cách làm việc. Không đưa dữ liệu đánh giá, kế hoạch hay thông tin nhận diện học sinh lên website."
        fields={[...heading().slice(0, 2), body(), visible()]} />
      <ListEditor resource="items" section="PROGRESS" title="Các ý chính" addLabel="Thêm ý" fixed={{ sectionKey: "PROGRESS" }}
        fields={[{ name: "title", label: "Tiêu đề", type: "text", required: true, max: 200 }, { name: "description", label: "Mô tả", type: "textarea", max: 2000, rows: 3 }, visible()]}
        columns={[{ title: "Nội dung", render: (r) => cell(text(r, "title"), text(r, "description")) }]} />
    </div>
  );
}

function Gallery() {
  return (
    <div className="wm-stack">
      <SingleEditor resource="sections" id="GALLERY" title="Tiêu đề phần không gian và hoạt động" fields={heading()} />
      <AlbumsEditor />
    </div>
  );
}

function Faqs() {
  return (
    <div className="wm-stack">
      <SingleEditor resource="sections" id="FAQ" title="Tiêu đề phần câu hỏi thường gặp" fields={heading()} />
      <ListEditor resource="faqs" title="Câu hỏi thường gặp" addLabel="Thêm câu hỏi"
        fields={[{ name: "question", label: "Câu hỏi", type: "text", required: true, max: 300 }, { name: "answer", label: "Câu trả lời", type: "textarea", required: true, max: 3000, rows: 5 }, visible()]}
        columns={[{ title: "Câu hỏi", render: (r) => cell(text(r, "question"), text(r, "answer")) }]} />
    </div>
  );
}

function Contact() {
  return (
    <div className="wm-stack">
      <SingleEditor resource="sections" id="CONTACT" title="Tiêu đề phần liên hệ" fields={heading()} />
      <SingleEditor resource="settings" id="WCFG001" title="Thông tin trung tâm và cấu hình website"
        hint="Mục để trống sẽ không hiển thị trên website. Liên kết phải bắt đầu bằng https://."
        fields={[
          { name: "centerName", label: "Tên trung tâm", type: "text", required: true, max: 200 },
          { name: "logoMediaId", label: "Logo", type: "image" },
          { name: "address", label: "Địa chỉ", type: "textarea", max: 500, rows: 2 },
          { name: "hotline", label: "Hotline", type: "text", max: 30 },
          { name: "workingHours", label: "Giờ làm việc", type: "text", max: 300 },
          { name: "zaloUrl", label: "Liên kết Zalo", type: "text", max: 300, help: "Ví dụ: https://zalo.me/số-điện-thoại" },
          { name: "facebookUrl", label: "Liên kết Facebook", type: "text", max: 300, help: "Ví dụ: https://www.facebook.com/tên-trang" },
          { name: "mapEmbedUrl", label: "Liên kết bản đồ nhúng", type: "text", max: 600, help: "Trong Google Maps chọn Chia sẻ → Nhúng bản đồ, chỉ dán phần địa chỉ trong src=\"…\"." },
        ]} />
    </div>
  );
}

const SECTIONS: { key: string; label: string; render: () => ReactElement }[] = [
  { key: "intro", label: "Giới thiệu trung tâm", render: () => <Intro /> },
  { key: "audience", label: "Đối tượng hỗ trợ", render: () => <Audience /> },
  { key: "services", label: "Chương trình và dịch vụ", render: () => <Services /> },
  { key: "team", label: "Đội ngũ và quy trình", render: () => <Team /> },
  { key: "progress", label: "Theo dõi tiến bộ", render: () => <Progress /> },
  { key: "gallery", label: "Không gian và hoạt động", render: () => <Gallery /> },
  { key: "faq", label: "Câu hỏi thường gặp", render: () => <Faqs /> },
  { key: "contact", label: "Liên hệ và cấu hình", render: () => <Contact /> },
  { key: "requests", label: "Yêu cầu tư vấn", render: () => <ConsultationManager /> },
];

export function WebsiteManager() {
  const [tab, setTab] = useState("intro");
  const { message, modal } = App.useApp();
  const [status, setStatus] = useState<WebsiteStatus | null>(null);
  const [error, setError] = useState("");
  const [publishing, setPublishing] = useState(false);

  const refresh = useCallback(async () => {
    try { setStatus(await websiteApi.status()); setError(""); } catch (e) { setError((e as Error).message); }
  }, []);
  useEffect(() => { void refresh(); }, [refresh]);
  const context = useMemo(() => ({ changed: () => void refresh() }), [refresh]);

  function publish() {
    modal.confirm({
      title: "Xuất bản website?",
      content: (
        <div>
          <p>Toàn bộ nội dung đang hiển thị trong bản nháp sẽ được công khai ngay cho khách truy cập.</p>
          {status && status.sampleCount > 0 && <p><b>Còn {status.sampleCount} mục là nội dung mẫu chưa được chỉnh sửa.</b> Hãy kiểm tra trước khi xuất bản.</p>}
        </div>
      ),
      okText: "Xuất bản", cancelText: "Chưa xuất bản",
      onOk: async () => {
        setPublishing(true);
        try { setStatus(await websiteApi.publish()); message.success("Đã xuất bản website."); }
        catch (e) { message.error((e as Error).message); }
        finally { setPublishing(false); }
      },
    });
  }

  return (
    <WebsiteContext.Provider value={context}>
      <div className="page-heading">
        <div>
          <div className="eyebrow">WEBSITE GIỚI THIỆU</div>
          <h1>Quản lý website</h1>
          <p>Soạn nội dung giới thiệu trung tâm. Lưu bản nháp không làm đổi website đang công khai cho đến khi bạn xuất bản.</p>
        </div>
      </div>
      <Card size="small" className="wm-status">
        {error ? <Alert type="error" showIcon message={error} action={<Button size="small" onClick={() => void refresh()}>Tải lại</Button>} />
          : !status ? <Spin /> : (
            <div className="wm-status-row">
              <div>
                {status.published ? <Tag color="green">Đã xuất bản</Tag> : <Tag color="orange">Chưa xuất bản</Tag>}
                {status.hasChanges && <Tag color="blue">Có thay đổi chưa xuất bản</Tag>}
                {status.sampleCount > 0 && <Tag color="gold">{status.sampleCount} mục nội dung mẫu</Tag>}
                <div className="wm-sub">
                  {status.published ? `Bản công khai: ${dayjs(status.publishedAt).format("DD/MM/YYYY HH:mm")} · ${status.publishedBy}` : "Khách truy cập sẽ thấy thông báo đang cập nhật cho đến khi xuất bản."}
                </div>
              </div>
              <div className="wm-buttons">
                <Button icon={<Eye size={16} />} onClick={() => window.open("#/gioi-thieu/xem-truoc", "_blank", "noopener")}>Xem trước bản nháp</Button>
                <Button icon={<Globe size={16} />} disabled={!status.published} onClick={() => window.open("#/gioi-thieu", "_blank", "noopener")}>Mở website công khai</Button>
                <Button type="primary" icon={<Send size={16} />} loading={publishing} disabled={status.published && !status.hasChanges} onClick={publish}>Xuất bản</Button>
              </div>
            </div>
          )}
      </Card>
      <Radio.Group className="wm-tabs" aria-label="Mục quản lý website" optionType="button" buttonStyle="solid" value={tab}
        onChange={(e) => setTab(e.target.value)} options={SECTIONS.map(({ key, label }) => ({ value: key, label }))} />
      <div className="wm-panel">{SECTIONS.find((s) => s.key === tab)?.render()}</div>
    </WebsiteContext.Provider>
  );
}
