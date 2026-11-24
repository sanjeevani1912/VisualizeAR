from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('api', '0002_login_throttle'),
    ]

    operations = [
        migrations.AddField(
            model_name='user',
            name='appointment',
            field=models.CharField(blank=True, default='', max_length=50),
        ),
    ]
