using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Interop;
using System.Windows.Markup;
using System.Windows.Media;
using System.Windows.Threading;
using Microsoft.Win32;

namespace YumisModSync;

public sealed class MainWindow : Window
{
    private readonly ServerProfile profile;
    private readonly bool admin;
    private readonly TextBlock stage = Text("准备就绪", 25, true);
    private readonly TextBlock detail = Text("正在准备本次同步", 14);
    private readonly TextBox log = new() { IsReadOnly = true, TextWrapping = TextWrapping.Wrap, VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
        Background = Brushes.Transparent, BorderThickness = new Thickness(0), Foreground = Brush("#C7CEE8"), FontSize = 13, FontFamily = new FontFamily("Microsoft YaHei UI"), Padding = new Thickness(4) };
    private readonly ProgressBar progressBar = new() { Height = 7, Minimum = 0, Maximum = 100, Foreground = Brush("#A298FF"), Background = Brush("#2D3559"), BorderThickness = new Thickness(0) };
    private readonly Button start = Button("重新检查", true);
    private readonly Button cancel = Button("取消");
    private readonly Button restore = Button("恢复上次备份");
    private readonly Button clear = Button("清理下载缓存");
    private readonly TextBox sourceBox = new() { MinHeight = 38, FontSize = 13, Padding = new Thickness(10, 8, 10, 8), Background = Brush("#10152B"), Foreground = Brushes.White, BorderBrush = Brush("#39436A"), BorderThickness = new Thickness(1) };
    private readonly Button browse = Button("选择 mods");
    private readonly IProgress<SyncProgress> reporter;
    private CancellationTokenSource? cancellation;
    private bool busy;
    private bool closeAfterWork;
    private string lastLogMessage = "";

    public MainWindow(ServerProfile profile, string[] args, bool autoRun = true)
    {
        this.profile = profile; admin = args.Contains("--publish");
        Title = admin ? "Yumis ModSync · 管理员发布" : "Yumis ModSync";
        Width = 1010; Height = 750; MinWidth = 820; MinHeight = 620; WindowStartupLocation = WindowStartupLocation.CenterScreen;
        Background = Brush("#0D1020"); Foreground = Brush("#ECF0FF"); FontFamily = new FontFamily("Microsoft YaHei UI");
        Resources.Add(typeof(Button), XamlReader.Parse("""
        <Style xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation" TargetType="Button">
          <Setter Property="Foreground" Value="#F1F2FF"/><Setter Property="Background" Value="#283155"/>
          <Setter Property="Padding" Value="17,10"/><Setter Property="Margin" Value="0,0,10,0"/>
          <Setter Property="BorderThickness" Value="0"/><Setter Property="FontSize" Value="13"/>
          <Setter Property="Cursor" Value="Hand"/>
          <Setter Property="Template"><Setter.Value><ControlTemplate TargetType="Button">
            <Border x:Name="bg" xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml" Background="{TemplateBinding Background}" CornerRadius="8" Padding="{TemplateBinding Padding}">
              <ContentPresenter HorizontalAlignment="Center" VerticalAlignment="Center"/></Border>
            <ControlTemplate.Triggers><Trigger Property="IsMouseOver" Value="True"><Setter TargetName="bg" Property="Opacity" Value="0.82"/></Trigger>
              <Trigger Property="IsEnabled" Value="False"><Setter TargetName="bg" Property="Opacity" Value="0.4"/></Trigger></ControlTemplate.Triggers>
          </ControlTemplate></Setter.Value></Setter>
        </Style>
        """));
        var outer = new Grid { Margin = new Thickness(34, 27, 34, 23) };
        outer.RowDefinitions.Add(new() { Height = GridLength.Auto });
        outer.RowDefinitions.Add(new() { Height = GridLength.Auto });
        outer.RowDefinitions.Add(new() { Height = GridLength.Auto });
        outer.RowDefinitions.Add(new() { Height = new GridLength(1, GridUnitType.Star) });
        outer.RowDefinitions.Add(new() { Height = GridLength.Auto });
        var header = new Grid { Margin = new Thickness(0, 0, 0, 24) };
        header.ColumnDefinitions.Add(new() { Width = GridLength.Auto }); header.ColumnDefinitions.Add(new());
        var badge = new Border { Width = 54, Height = 54, CornerRadius = new CornerRadius(16), Background = new LinearGradientBrush(Brush("#647AFF").Color, Brush("#A478EE").Color, 45),
            Child = new TextBlock { Text = "Y", FontWeight = FontWeights.Bold, FontSize = 32, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center, Foreground = Brushes.White } };
        header.Children.Add(badge);
        var brand = new StackPanel { Margin = new Thickness(17, 0, 0, 0) }; Grid.SetColumn(brand, 1);
        brand.Children.Add(Text("YUMIS  /  MODSYNC", 13, true, "#A9B8FA"));
        brand.Children.Add(Text(admin ? "发布你的模组版本" : "让模组，保持同步。", 26, true)); header.Children.Add(brand);
        outer.Children.Add(header);
        var info = new StackPanel { Margin = new Thickness(0, 0, 0, 17) };
        info.Children.Add(Text(admin ? "精确识别外部来源，仅上传需要托管的 JAR。" : "检查 → 下载 → 校验 → 备份替换 → 打开 PCL", 13, false, "#9CAACD"));
        info.Children.Add(Text("Minecraft 26.2  ·  NeoForge 26.2.0.93  ·  官方 PCL 2.13.1.1 协作", 12, false, "#7F8CAF"));
        if (admin)
        {
            var sourceRow = new DockPanel { Margin = new Thickness(0, 14, 0, 0) };
            DockPanel.SetDock(browse, Dock.Right); browse.Margin = new Thickness(10, 0, 0, 0); sourceRow.Children.Add(browse); sourceRow.Children.Add(sourceBox);
            sourceBox.Text = Program.Argument(args, "--source") ?? DefaultSource(); info.Children.Add(sourceRow);
        }
        Grid.SetRow(info, 1); outer.Children.Add(info);
        var state = new StackPanel(); stage.Margin = new Thickness(0, 0, 0, 8); detail.Margin = new Thickness(0, 0, 0, 18); detail.TextWrapping = TextWrapping.Wrap;
        state.Children.Add(stage); state.Children.Add(detail); state.Children.Add(progressBar);
        var stateCard = new Border { Background = Brush("#1A213D"), CornerRadius = new CornerRadius(14), Padding = new Thickness(22), Child = state, Margin = new Thickness(0, 0, 0, 17) };
        Grid.SetRow(stateCard, 2); outer.Children.Add(stateCard);
        var logPanel = new DockPanel(); var logTitle = Text("本次记录", 12, true, "#9AA9D2"); logTitle.Margin = new Thickness(4, 0, 0, 9); DockPanel.SetDock(logTitle, Dock.Top); logPanel.Children.Add(logTitle); logPanel.Children.Add(log);
        var logCard = new Border { Background = Brush("#12172D"), CornerRadius = new CornerRadius(14), Padding = new Thickness(17), Child = logPanel, Margin = new Thickness(0, 0, 0, 20) };
        Grid.SetRow(logCard, 3); outer.Children.Add(logCard);
        var footer = new DockPanel(); var right = new StackPanel { Orientation = Orientation.Horizontal }; DockPanel.SetDock(right, Dock.Right);
        cancel.IsEnabled = false; right.Children.Add(cancel); right.Children.Add(start); start.Margin = new Thickness(0);
        footer.Children.Add(right);
        var left = new StackPanel { Orientation = Orientation.Horizontal }; left.Children.Add(restore); left.Children.Add(clear); footer.Children.Add(left);
        Grid.SetRow(footer, 4); outer.Children.Add(footer); Content = outer;
        if (admin) { start.Content = "发布更新"; restore.Visibility = clear.Visibility = Visibility.Collapsed; }
        reporter = new ActionProgress(value => { if (Dispatcher.CheckAccess()) UpdateProgress(value); else Dispatcher.Invoke(() => UpdateProgress(value)); });
        start.Click += async (_, _) => await RunAsync(admin ? "publish" : "sync");
        cancel.Click += (_, _) => { cancellation?.Cancel(); detail.Text = "正在取消并清理，请稍候…"; };
        restore.Click += async (_, _) => await RunAsync("restore"); clear.Click += async (_, _) => await RunAsync("clear");
        browse.Click += (_, _) => { var picker = new OpenFolderDialog { Title = "选择实例的 mods 目录" }; if (picker.ShowDialog(this) == true) sourceBox.Text = picker.FolderName; };
        Closing += (_, e) => { if (busy) { e.Cancel = true; closeAfterWork = true; cancellation?.Cancel(); detail.Text = "正在取消并恢复，请等待清理完成后关闭…"; } };
        SourceInitialized += (_, _) => { var value = 1; DwmSetWindowAttribute(new WindowInteropHelper(this).Handle, 20, ref value, sizeof(int)); };
        if (autoRun && !admin) ContentRendered += async (_, _) => { if (!busy) await RunAsync("sync"); };
    }

    private string DefaultSource()
    {
        try { return InstanceCatalog.Find(AppContext.BaseDirectory, new ReleaseManifest()).FirstOrDefault()?.ModsDirectory ?? ""; } catch { return ""; }
    }
    private async Task RunAsync(string action)
    {
        if (busy) return;
        busy = true; start.IsEnabled = restore.IsEnabled = clear.IsEnabled = browse.IsEnabled = false; cancel.IsEnabled = true;
        cancellation = new CancellationTokenSource(); var ct = cancellation.Token; var source = sourceBox.Text;
        try
        {
            await Task.Run(async () =>
            {
                if (action == "publish")
                {
                    var result = await new Publisher(profile, reporter).PublishAsync(source, Path.Combine(AppContext.BaseDirectory, "temp", "YumisModSync-admin"), ct);
                    reporter.Report(new("发布完成", $"{result.ReleaseId} · 清单 {result.Files} 个 · 上传 {result.UploadedFiles} 个 JAR", 100));
                    return;
                }
                var workspace = new Workspace(AppContext.BaseDirectory);
                var engine = new SyncEngine(workspace, profile, reporter, DecideCloseAsync, ChooseInstanceAsync);
                switch (action)
                {
                    case "restore": await engine.RestoreAsync(ct); break;
                    case "clear": await engine.ClearCacheAsync(ct); break;
                    default: await engine.SyncAsync(ct); break;
                }
            });
        }
        catch (OperationCanceledException) { UpdateProgress(new("已取消", "未完成的替换会先恢复；可以重新检查。")); }
        catch (Exception error) { UpdateProgress(new("未完成", error.Message)); }
        finally
        {
            busy = false; start.IsEnabled = restore.IsEnabled = clear.IsEnabled = browse.IsEnabled = true; cancel.IsEnabled = false;
            progressBar.IsIndeterminate = false;
            cancellation.Dispose(); cancellation = null;
            if (closeAfterWork) Close();
        }
    }
    private void UpdateProgress(SyncProgress value)
    {
        stage.Text = value.Stage; detail.Text = value.Detail;
        progressBar.IsIndeterminate = value.Percent == null && busy;
        if (value.Percent != null) progressBar.Value = Math.Clamp(value.Percent.Value, 0, 100);
        if (lastLogMessage == value.Stage + value.Detail) return;
        lastLogMessage = value.Stage + value.Detail;
        var line = $"{DateTimeOffset.Now.ToOffset(TimeSpan.FromHours(8)):HH:mm:ss}  [{value.Stage}] {value.Detail}\n";
        if (log.Text.Length > 160000) log.Text = log.Text[^80000..];
        log.AppendText(line); log.ScrollToEnd();
    }
    private async Task<CloseChoice> DecideCloseAsync(IReadOnlyList<PclProcess> processes) =>
        await Dispatcher.InvokeAsync(() =>
        {
            var dialog = new ClosePclWindow(processes) { Owner = this }; dialog.ShowDialog(); return dialog.Choice;
        });

    private async Task<GameInstance?> ChooseInstanceAsync(IReadOnlyList<GameInstance> instances) =>
        await Dispatcher.InvokeAsync(() =>
        {
            var dialog = new Window { Title = "选择要同步的实例", Width = 650, Height = 350, Owner = this, WindowStartupLocation = WindowStartupLocation.CenterOwner,
                Background = Brush("#171D34"), Foreground = Brushes.White, ResizeMode = ResizeMode.NoResize };
            var panel = new DockPanel { Margin = new Thickness(22) };
            var button = Button("使用此实例", true); DockPanel.SetDock(button, Dock.Bottom); panel.Children.Add(button);
            var list = new ListBox { ItemsSource = instances, DisplayMemberPath = "Name", Background = Brush("#10152B"), Foreground = Brushes.White, FontSize = 16, SelectedIndex = 0, Margin = new Thickness(0, 0, 0, 16) }; panel.Children.Add(list);
            dialog.Content = panel; GameInstance? chosen = null;
            button.Click += (_, _) => { chosen = list.SelectedItem as GameInstance; dialog.Close(); }; dialog.ShowDialog(); return chosen;
        });

    internal static SolidColorBrush Brush(string color) => (SolidColorBrush)new BrushConverter().ConvertFromString(color)!;
    internal static TextBlock Text(string text, double size, bool bold = false, string color = "#ECF0FF") => new()
    { Text = text, FontSize = size, FontWeight = bold ? FontWeights.SemiBold : FontWeights.Normal, Foreground = Brush(color), TextWrapping = TextWrapping.Wrap };
    internal static Button Button(string title, bool primary = false) => new() { Content = title, Background = Brush(primary ? "#7166E7" : "#283155"), Foreground = Brushes.White, BorderThickness = new Thickness(0) };
    [DllImport("dwmapi.dll")] private static extern int DwmSetWindowAttribute(IntPtr hwnd, int attr, ref int value, int size);
}

public sealed class ClosePclWindow : Window
{
    public CloseChoice Choice { get; private set; } = CloseChoice.Cancel;
    public ClosePclWindow(IReadOnlyList<PclProcess> processes, TimeSpan? countdown = null)
    {
        Title = "等待关闭 PCL"; Width = 640; Height = 365; ResizeMode = ResizeMode.NoResize; WindowStartupLocation = WindowStartupLocation.CenterOwner;
        Background = MainWindow.Brush("#171D34"); Foreground = Brushes.White; FontFamily = new FontFamily("Microsoft YaHei UI");
        var panel = new StackPanel { Margin = new Thickness(25) }; Content = panel;
        panel.Children.Add(MainWindow.Text("PCL 尚未退出", 24, true));
        panel.Children.Add(MainWindow.Text("正常关闭已等待 15 秒。强制结束会中断启动器的下载任务。", 14));
        var timerText = MainWindow.Text("", 17, true, "#B9ADFF"); timerText.Margin = new Thickness(0, 17, 0, 10); panel.Children.Add(timerText);
        panel.Children.Add(MainWindow.Text(string.Join("\n", processes.Select(p => $"PCL · PID {p.Id} · {Path.GetFileName(p.Path)}")), 12, false, "#A4B3D8"));
        var buttons = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 23, 0, 0) }; panel.Children.Add(buttons);
        foreach (var (text, choice) in new[] { ("立即强制结束", CloseChoice.Force), ("继续等待", CloseChoice.Wait), ("取消同步", CloseChoice.Cancel) })
        {
            var button = MainWindow.Button(text, choice == CloseChoice.Force); button.Padding = new Thickness(15, 10, 15, 10); button.Margin = new Thickness(0, 0, 10, 0);
            button.Click += (_, _) => { Choice = choice; Close(); }; buttons.Children.Add(button);
        }
        var deadline = DateTime.UtcNow + (countdown ?? TimeSpan.FromSeconds(60));
        var timer = new DispatcherTimer { Interval = TimeSpan.FromMilliseconds(200) };
        timer.Tick += (_, _) =>
        {
            if (!processes.Any(ProcessControl.IsAlive)) { Choice = CloseChoice.Wait; Close(); return; }
            var seconds = Math.Max(0, (int)Math.Ceiling((deadline - DateTime.UtcNow).TotalSeconds));
            timerText.Text = $"{seconds} 秒后自动结束上述 PCL 进程";
            if (seconds == 0) { Choice = CloseChoice.Force; Close(); }
        };
        Loaded += (_, _) => timer.Start(); Closed += (_, _) => timer.Stop();
    }
}
